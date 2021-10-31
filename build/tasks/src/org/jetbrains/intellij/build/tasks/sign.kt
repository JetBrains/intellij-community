// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("ReplaceGetOrSet")

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
    artifactBuilt = {}
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
          for (entry in zipFile.entriesInPhysicalOrder) {
            zipOutStream.addRawArchiveEntry(entry, zipFile.getRawInputStream(entry))
          }

          if (macAdditionalDir != null) {
            addDirToZip(macAdditionalDir, zipOutStream, prefix = "$zipRoot/")
          }

          zipOutStream.putArchiveEntry(ZipArchiveEntry(productJsonZipPath))
          zipOutStream.write(productJson)
          zipOutStream.closeArchiveEntry()
        }
      }
    }
  }
}

// a log file is saved as an artifact and not as a log file to `logs` to make it possible to inspect it before a build finish
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
): List<Path> {
  System.setProperty("log4j.defaultInitOverride", "true")
  val root: org.apache.log4j.Logger = org.apache.log4j.Logger.getRootLogger()
  if (!root.allAppenders.hasMoreElements()) {
    root.level = Level.INFO
    root.addAppender(ConsoleAppender(PatternLayout(PatternLayout.DEFAULT_CONVERSION_PATTERN)))
  }

  val currentDateTimeString = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME).replace(':', '-')
  val remoteDir = "$remoteDirPrefix-$currentDateTimeString-${random.nextLong().toString(Character.MAX_RADIX)}"

  val defaultConfig = DefaultConfig()
  defaultConfig.keepAliveProvider = KeepAliveProvider.KEEP_ALIVE

  val ssh = SSHClient(defaultConfig)
  ssh.addHostKeyVerifier(PromiscuousVerifier())
  ssh.connect(host)
  val failedToSign = mutableListOf<Path>()
  try {
    ssh.authPassword(user, password)
    try {
      ssh.newSFTPClient().use { ftpClient ->
        ftpClient.mkdir(remoteDir)
        // 0777 octal -> 511 decimal
        val remoteSignScript = "$remoteDir/${signScript.fileName}"
        ftpClient.put(NioFileSource(signScript, 511), remoteSignScript)

        for (file in files) {
          tracer.spanBuilder("sign").setAttribute("file", file.toString()).startSpan().useWithScope {
            signFile(remoteDir = remoteDir,
                     file = file,
                     ssh = ssh,
                     ftpClient = ftpClient,
                     remoteSignScript = remoteSignScript,
                     user = user,
                     password = password,
                     codesignString = codesignString,
                     artifactDir = artifactDir,
                     failedToSign = failedToSign,
                     artifactBuilt = artifactBuilt)
          }
        }
      }
    }
    finally {
      // as odd as it is, session can only be used once
      // https://stackoverflow.com/a/23467751
      removeDir(ssh, remoteDir)
    }
  }
  finally {
    ssh.disconnect()
  }

  return failedToSign
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

private fun signFile(remoteDir: String,
                     file: Path,
                     ssh: SSHClient,
                     ftpClient: SFTPClient,
                     remoteSignScript: String,
                     user: String,
                     password: String,
                     codesignString: String,
                     artifactDir: Path,
                     failedToSign: MutableList<Path>,
                     artifactBuilt: Consumer<Path>) {
  val fileName = file.fileName.toString()
  val remoteFile = "$remoteDir/$fileName"
  ftpClient.put(NioFileSource(file), remoteFile)

  val span = Span.current()

  val logFileName = "sign-$fileName.log"
  var commandFailed: String? = null
  val logFile = artifactDir.resolve("macos-sign-logs").resolve(logFileName)
  try {
    Files.createDirectories(logFile.parent)

    ssh.startSession().use { session ->
      @Suppress("SpellCheckingInspection")
      // not a relative path to file is expected, but only a file name
      val command = session.exec("'$remoteSignScript' '$fileName' '$user' '$password' '$codesignString'")
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
            failedToSign.add(file)
            Files.deleteIfExists(tempFile)
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