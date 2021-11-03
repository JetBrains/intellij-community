// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("ReplaceGetOrSet", "ReplaceNegatedIsEmptyWithIsNotEmpty")

package org.jetbrains.intellij.build.tasks

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import net.schmizz.keepalive.KeepAliveProvider
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveEntryPredicate
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.log4j.ConsoleAppender
import org.apache.log4j.Level
import org.apache.log4j.PatternLayout
import org.jetbrains.intellij.build.io.NioFileDestination
import org.jetbrains.intellij.build.io.NioFileSource
import org.jetbrains.intellij.build.io.writeNewFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.SecureRandom
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import java.util.zip.Deflater
import kotlin.concurrent.thread

private val random by lazy { SecureRandom() }

fun main() {
  @Suppress("SpellCheckingInspection")
  signMac(
    host = "localhost",
    user = System.getProperty("ssh.user"),
    password = System.getProperty("ssh.password"),
    codesignString = "",
    remoteDirPrefix = "test",
    signScript = Path.of("/Volumes/data/Documents/idea/community/platform/build-scripts/tools/mac/scripts/signbin.sh"),
    files = listOf(Path.of("/Applications/Idea.app/Contents/bin/fsnotifier2")),
    artifactDir = Path.of("/tmp/signed-files"),
    artifactBuilt = {},
  )
}

// our zip for JARs, but here we need to support file permissions - that's why apache compress is used
fun prepareMacZip(macZip: Path,
                  sitFile: Path,
                  productJson: ByteArray,
                  macAdditionalDir: Path?,
                  zipRoot: String) {
  Files.newByteChannel(macZip, StandardOpenOption.READ).use { sourceFileChannel ->
    ZipFile(sourceFileChannel).use { zipFile ->
      writeNewFile(sitFile) { targetFileChannel ->
        ZipArchiveOutputStream(targetFileChannel).use { zipOutStream ->
          // file just used for transfer
          zipOutStream.setLevel(Deflater.BEST_SPEED)

          // exclude existing product-info.json as a custom one will be added
          val productJsonZipPath = "$zipRoot/Resources/product-info.json"
          zipFile.copyRawEntries(zipOutStream, ZipArchiveEntryPredicate { it.name != productJsonZipPath })
          if (macAdditionalDir != null) {
            zipOutStream.dir(macAdditionalDir, prefix = "$zipRoot/")
          }

          zipOutStream.putArchiveEntry(ZipArchiveEntry(productJsonZipPath))
          zipOutStream.write(productJson)
          zipOutStream.closeArchiveEntry()
        }
      }
    }
  }
}

// 0644 octal -> 420 decimal
private const val regularFileMode = 420
// 0777 octal -> 511 decimal
private const val executableFileMode = 511

@Suppress("unused")
fun signMacZip(
  host: String,
  user: String,
  password: String,
  codesignString: String,
  fullBuildNumber: String,
  notarize: Boolean,
  bundleIdentifier: String,
  file: Path,
  jreArchiveFile: Path?,
  communityHome: Path,
  artifactDir: Path,
  artifactBuilt: Consumer<Path>
) {
  initLog

  executeTask(host, user, password, "intellij-builds/${fullBuildNumber}") { ssh, sftp, remoteDir ->
    tracer.spanBuilder("upload file")
      .setAttribute("file", file.toString())
      .setAttribute("remoteDir", remoteDir)
      .setAttribute("host", host)
      .startSpan().use {
        sftp.put(NioFileSource(file, filePermission = regularFileMode), "$remoteDir/${file.fileName}")
      }

    if (jreArchiveFile != null) {
      tracer.spanBuilder("upload JRE archive")
        .setAttribute("file", jreArchiveFile.toString())
        .setAttribute("remoteDir", remoteDir)
        .setAttribute("host", host)
        .startSpan().use {
          sftp.put(NioFileSource(jreArchiveFile, filePermission = regularFileMode), "$remoteDir/${jreArchiveFile.fileName}")
        }
    }

    val scriptDir = communityHome.resolve("platform/build-scripts/tools/mac/scripts")
    tracer.spanBuilder("upload scripts")
      .setAttribute("scriptDir", scriptDir.toString())
      .setAttribute("remoteDir", remoteDir)
      .setAttribute("host", host)
      .startSpan().use {
        sftp.put(NioFileSource(scriptDir.resolve("entitlements.xml"), filePermission = regularFileMode), "entitlements.xml")
        @Suppress("SpellCheckingInspection")
        for (fileName in listOf("sign.sh", "notarize.sh", "signapp.sh")) {
          sftp.put(NioFileSource(scriptDir.resolve(fileName), filePermission = executableFileMode), "$remoteDir/$fileName")
        }
      }

    val args = listOf(
      file.fileName.toString(),
      fullBuildNumber,
      user,
      password,
      codesignString,
      jreArchiveFile?.fileName?.toString() ?: "no-jdk",
      if (notarize) "yes" else "no",
      bundleIdentifier,
    )
    @Suppress("SpellCheckingInspection")
    signFile(remoteDir = remoteDir,
             commandString = "'$remoteDir/signapp.sh' '${args.joinToString("' '")}'",
             file = file,
             ssh = ssh,
             ftpClient = sftp,
             artifactDir = artifactDir,
             artifactBuilt = artifactBuilt,
             failedToSign = null)

  }
}

fun signMac(
  host: String,
  user: String,
  password: String,
  codesignString: String,
  remoteDirPrefix: String,
  signScript: Path,
  files: List<Path>,
  artifactDir: Path,
  artifactBuilt: Consumer<Path>
) {
  initLog

  val failedToSign = mutableListOf<Path>()
  executeTask(host, user, password, remoteDirPrefix) { ssh, sftp, remoteDir ->
    val remoteSignScript = "$remoteDir/${signScript.fileName}"
    sftp.put(NioFileSource(signScript, executableFileMode), remoteSignScript)

    for (file in files) {
      tracer.spanBuilder("sign").setAttribute("file", file.toString()).startSpan().useWithScope {
        signFile(remoteDir = remoteDir,
                 commandString = "'$remoteSignScript' '${file.fileName}' '$user' '$password' '$codesignString'",
                 file = file,
                 ssh = ssh,
                 ftpClient = sftp,
                 artifactDir = artifactDir,
                 artifactBuilt = artifactBuilt,
                 failedToSign = failedToSign)
      }
    }
  }

  if (!failedToSign.isEmpty()) {
    throw RuntimeException("Failed to sign files: ${failedToSign.joinToString { it.toString() }}")
  }
}

private fun signFile(remoteDir: String,
                     file: Path,
                     ssh: SSHClient,
                     ftpClient: SFTPClient,
                     commandString: String,
                     artifactDir: Path,
                     artifactBuilt: Consumer<Path>,
                     failedToSign: MutableList<Path>?) {
  val fileName = file.fileName.toString()
  val remoteFile = "$remoteDir/$fileName"
  ftpClient.put(NioFileSource(file), remoteFile)

  val span = Span.current()

  val logFileName = "sign-$fileName.log"
  var commandFailed: String? = null
  val logFile = artifactDir.resolve("macos-sign-logs").resolve(logFileName)
  Files.createDirectories(logFile.parent)
  try {
    ssh.startSession().use { session ->
      System.getenv("ARTIFACTORY_URL")?.takeIf { it.isNotEmpty() }?.let {
        session.setEnvVar("ARTIFACTORY_URL", it)
      }

      @Suppress("SpellCheckingInspection")
      // not a relative path to file is expected, but only a file name
      val command = session.exec(commandString)
      val inputStreamReadThread = thread(name = "error-stream-reader-of-sign-$fileName") {
        command.inputStream.transferTo(System.out)
      }
      command.errorStream.use {
        Files.copy(it, logFile, StandardCopyOption.REPLACE_EXISTING)
      }
      inputStreamReadThread.join(TimeUnit.HOURS.toMillis(3))

      command.join(1, TimeUnit.MINUTES)

      command.close()
      if (command.exitStatus != 0) {
        commandFailed = "cannot sign, details are available in ${artifactDir.relativize(logFile)}" +
                        " (exitStatus=${command.exitStatus}, exitErrorMessage=${command.exitErrorMessage})"
      }
    }
  }
  catch (e: Exception) {
    throw RuntimeException("SSH command failed, details are available in ${artifactDir.relativize(logFile)}: ${e.message}", e)
  }
  finally {
    if (Files.exists(logFile)) {
      artifactBuilt.accept(logFile)
    }
  }

  if (commandFailed != null) {
    throw RuntimeException(commandFailed)
  }

  tracer.spanBuilder("download signed file")
    .setAttribute("remoteFile", remoteFile)
    .setAttribute("localFile", file.toString())
    .startSpan()
    .use {
      val tempFile = file.parent.resolve("$fileName.download")
      var attempt = 1
      do {
        try {
          ftpClient.get(remoteFile, NioFileDestination(tempFile))
        }
        catch (e: Exception) {
          span.addEvent("cannot download $remoteFile", Attributes.of(
            AttributeKey.longKey("attemptNumber"), attempt.toLong(),
            AttributeKey.stringKey("error"), e.toString(),
            AttributeKey.stringKey("remoteFile"), remoteFile,
          ))
          attempt++
          if (attempt > 3) {
            Files.deleteIfExists(tempFile)
            if (failedToSign == null) {
              throw RuntimeException("Failed to sign file: $file")
            }
            else {
              failedToSign.add(file)
            }
            return
          }
          else {
            continue
          }
        }

        break
      }
      while (true)

      if (attempt != 1) {
        span.addEvent("signed file was downloaded", Attributes.of(
          AttributeKey.longKey("attemptNumber"), attempt.toLong(),
        ))
      }
      Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING)
    }
}

private val initLog by lazy {
  System.setProperty("log4j.defaultInitOverride", "true")
  val root: org.apache.log4j.Logger = org.apache.log4j.Logger.getRootLogger()
  if (!root.allAppenders.hasMoreElements()) {
    root.level = Level.INFO
    root.addAppender(ConsoleAppender(PatternLayout(PatternLayout.DEFAULT_CONVERSION_PATTERN)))
  }
}

private fun generateRemoteDirName(remoteDirPrefix: String): String {
  val currentDateTimeString = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME).replace(':', '-')
  return "$remoteDirPrefix-$currentDateTimeString-${random.nextLong().toString(Character.MAX_RADIX)}"
}

private inline fun executeTask(host: String,
                               user: String,
                               password: String,
                               remoteDirPrefix: String,
                               task: (ssh: SSHClient, sftp: SFTPClient, remoteDir: String) -> Unit) {
  val config = DefaultConfig()
  config.keepAliveProvider = KeepAliveProvider.KEEP_ALIVE

  val ssh = SSHClient(config)
  ssh.use {
    ssh.addHostKeyVerifier(PromiscuousVerifier())
    ssh.connect(host)
    ssh.authPassword(user, password)

    ssh.newSFTPClient().use { sftp ->
      val remoteDir = generateRemoteDirName(remoteDirPrefix)
      sftp.mkdir(remoteDir)
      try {
        task(ssh, sftp, remoteDir)
      }
      finally {
        // as odd as it is, session can only be used once
        // https://stackoverflow.com/a/23467751
        removeDir(ssh, remoteDir)
      }
    }
  }
}

private fun removeDir(ssh: SSHClient, remoteDir: String) {
  tracer.spanBuilder("remove remote dir").setAttribute("remoteDir", remoteDir).startSpan().use {
    ssh.startSession().use { session ->
      val command = session.exec("rm -rf '$remoteDir'")
      command.join(30, TimeUnit.SECONDS)
      // must be called before checking exit code
      command.close()
      if (command.exitStatus != 0) {
        throw RuntimeException("cannot remove remote directory (exitStatus=${command.exitStatus}, " +
                               "exitErrorMessage=${command.exitErrorMessage})")
      }
    }
  }
}