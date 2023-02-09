// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.build.impl

import com.intellij.diagnostic.telemetry.use
import com.intellij.diagnostic.telemetry.useWithScope
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.NioFiles
import com.jcraft.jsch.agentproxy.AgentProxy
import com.jcraft.jsch.agentproxy.AgentProxyException
import com.jcraft.jsch.agentproxy.Connector
import com.jcraft.jsch.agentproxy.ConnectorFactory
import com.jcraft.jsch.agentproxy.sshj.AuthAgent
import com.jetbrains.signatureverifier.ILogger
import com.jetbrains.signatureverifier.InvalidDataException
import com.jetbrains.signatureverifier.crypt.SignatureVerificationParams
import com.jetbrains.signatureverifier.crypt.SignedMessage
import com.jetbrains.signatureverifier.crypt.SignedMessageVerifier
import com.jetbrains.signatureverifier.crypt.VerifySignatureStatus
import com.jetbrains.signatureverifier.macho.MachoArch
import com.jetbrains.util.filetype.FileProperties
import com.jetbrains.util.filetype.FileType
import com.jetbrains.util.filetype.FileTypeDetector.DetectFileType
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.schmizz.keepalive.KeepAliveProvider
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.PatchedSSHClient
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.Channel
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.userauth.method.AuthKeyboardInteractive
import net.schmizz.sshj.userauth.method.AuthMethod
import net.schmizz.sshj.userauth.method.AuthPassword
import net.schmizz.sshj.userauth.method.PasswordResponseProvider
import net.schmizz.sshj.userauth.password.PasswordFinder
import net.schmizz.sshj.userauth.password.Resource
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.TraceManager.spanBuilder
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.executeStep
import org.jetbrains.intellij.build.io.*
import org.jetbrains.intellij.build.retryWithExponentialBackOff
import org.jetbrains.jps.api.GlobalOptions
import java.io.InputStream
import java.io.OutputStream
import java.nio.channels.SeekableByteChannel
import java.nio.charset.StandardCharsets
import java.nio.file.*
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFilePermission
import java.security.SecureRandom
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.runAsync
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import java.util.logging.*
import java.util.logging.Formatter
import kotlin.io.path.*

private val random by lazy { SecureRandom() }

// 0644 octal -> 420 decimal
private const val regularFileMode = 420
// 0777 octal -> 511 decimal
private const val executableFileMode = 511

internal fun notarizeAndBuildDmg(
  context: BuildContext,
  host: String,
  user: String,
  password: String,
  codesignString: String,
  fullBuildNumber: String,
  notarize: Boolean,
  bundleIdentifier: String,
  appArchiveFile: Path,
  communityHome: BuildDependenciesCommunityRoot,
  artifactDir: Path,
  dmgImage: Path?,
  artifactBuilt: Consumer<Path>,
  publishAppArchive: Boolean
) {
  require(notarize || dmgImage != null) {
    "Both Apple Notarization and .dmg creation are disabled"
  }
  executeTask(host, user, password, "intellij-builds/${fullBuildNumber}") { ssh, sftp, remoteDir ->
    spanBuilder("upload file")
      .setAttribute("file", appArchiveFile.toString())
      .setAttribute("remoteDir", remoteDir)
      .setAttribute("host", host)
      .use {
        sftp.put(NioFileSource(appArchiveFile, filePermission = regularFileMode), "$remoteDir/${appArchiveFile.fileName}")
      }

    val scriptDir = communityHome.communityRoot.resolve("platform/build-scripts/tools/mac/scripts")
    spanBuilder("upload scripts")
      .setAttribute("scriptDir", scriptDir.toString())
      .setAttribute("remoteDir", remoteDir)
      .setAttribute("host", host)
      .use {
        sftp.put(NioFileSource(scriptDir.resolve("entitlements.xml"), filePermission = regularFileMode), "$remoteDir/entitlements.xml")
        for (fileName in listOf("notarize.sh", "signapp.sh", "makedmg.sh", "makedmg.py")) {
          sftp.put(NioFileSource(scriptDir.resolve(fileName), filePermission = executableFileMode), "$remoteDir/$fileName")
        }

        if (dmgImage != null) {
          sftp.put(NioFileSource(dmgImage, filePermission = regularFileMode), "$remoteDir/$fullBuildNumber.png")
        }
      }

    val args = listOf(
      appArchiveFile.name,
      fullBuildNumber,
      if (notarize) "yes" else "no",
      bundleIdentifier,
      codesignString,
      publishAppArchive.toString()
    )
    val buildDate = "${GlobalOptions.BUILD_DATE_IN_SECONDS}=${context.options.buildDateInSeconds}"
    val env = sequenceOf("ARTIFACTORY_URL", "SERVICE_ACCOUNT_NAME", "SERVICE_ACCOUNT_TOKEN")
      .map { "$it=${System.getenv(it)}" }
      .plus(buildDate)
      .joinToString(separator = " ", postfix = " ")
    spanBuilder("sign mac app").setAttribute("file", appArchiveFile.toString()).useWithScope {
      signFile(remoteDir = remoteDir,
               commandString = "$env'$remoteDir/signapp.sh' '${args.joinToString("' '")}'",
               file = appArchiveFile,
               ssh = ssh,
               ftpClient = sftp,
               artifactDir = artifactDir,
               artifactBuilt = artifactBuilt)
      if (publishAppArchive) {
        downloadResult(remoteFile = "$remoteDir/${appArchiveFile.fileName}",
                       localFile = appArchiveFile,
                       ftpClient = sftp)
      }
    }

    if (publishAppArchive) {
      artifactBuilt.accept(appArchiveFile)
    }

    if (dmgImage != null) {
      val fileNameWithoutExt = appArchiveFile.name.removeSuffix(".sit")
      val dmgFile = artifactDir.resolve("$fileNameWithoutExt.dmg")
      spanBuilder("build dmg").setAttribute("file", dmgFile.toString()).useWithScope {
        processFile(localFile = dmgFile,
                    ssh = ssh,
                    commandString = "$buildDate /bin/bash -l '$remoteDir/makedmg.sh' '$fileNameWithoutExt' '$fullBuildNumber'",
                    artifactDir = artifactDir,
                    artifactBuilt = artifactBuilt,
                    taskLogClassifier = "dmg")
        downloadResult(remoteFile = "$remoteDir/${dmgFile.fileName}",
                       localFile = dmgFile,
                       ftpClient = sftp)

        artifactBuilt.accept(dmgFile)
      }
    }
  }
}

private fun signFile(remoteDir: String,
                        file: Path,
                        ssh: SSHClient,
                        ftpClient: SFTPClient,
                        commandString: String,
                        artifactDir: Path,
                        artifactBuilt: Consumer<Path>) {
  ftpClient.put(NioFileSource(file), "$remoteDir/${file.fileName}")
  processFile(localFile = file,
              ssh = ssh,
              commandString = commandString,
              artifactDir = artifactDir,
              artifactBuilt = artifactBuilt,
              taskLogClassifier = "sign")
}

private fun processFile(localFile: Path,
                        ssh: SSHClient,
                        commandString: String,
                        artifactDir: Path,
                        artifactBuilt: Consumer<Path>,
                        taskLogClassifier: String) {
  val fileName = localFile.fileName.toString()

  val logFile = artifactDir.resolve("macos-logs").resolve("$taskLogClassifier-$fileName.log")
  Files.createDirectories(logFile.parent)
  ssh.startSession().use { session ->
    val command = session.exec(commandString)
    try {
      logFile.outputStream(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING).use { logStream ->
        // use CompletableFuture because get will call ForkJoinPool.helpAsyncBlocker, so, other tasks in FJP will be executed while waiting
        CompletableFuture.allOf(
          runAsync { command.inputStream.writeEachLineTo(logStream, System.out, channel = command) },
          runAsync { command.errorStream.writeEachLineTo(logStream, System.out, channel = command) },
        ).get(6, TimeUnit.HOURS)
      }
      command.join(1, TimeUnit.MINUTES)
    }
    catch (e: Exception) {
      val logFileLocation = if (Files.exists(logFile)) artifactDir.relativize(logFile) else "<internal error - log file is not created>"
      throw RuntimeException("SSH command failed, details are available in $logFileLocation: ${e.message}", e)
    }
    finally {
      if (Files.exists(logFile)) {
        artifactBuilt.accept(logFile)
      }
      command.close()
    }

    if (command.exitStatus != 0) {
      throw RuntimeException("SSH command failed, details are available in ${artifactDir.relativize(logFile)}" +
                             " (exitStatus=${command.exitStatus}, exitErrorMessage=${command.exitErrorMessage})")
    }
  }
}

private fun InputStream.writeEachLineTo(vararg outputStreams: OutputStream, channel: Channel) {
  val lineBuffer = StringBuilder()
  fun writeLine() {
    val lineBytes = lineBuffer.toString().toByteArray()
    outputStreams.forEach {
      synchronized(it) {
        it.write(lineBytes)
      }
    }
  }
  bufferedReader().use { reader ->
    while (channel.isOpen || reader.ready()) {
      if (reader.ready()) {
        val char = reader.read()
          .takeIf { it != -1 }?.toChar()
          ?.also(lineBuffer::append)
        val endOfLine = char == '\n' || char == '\r' || char == null
        if (endOfLine && lineBuffer.isNotEmpty()) {
          writeLine()
          lineBuffer.clear()
        }
      }
      else {
        Thread.sleep(100L)
      }
    }
    if (lineBuffer.isNotBlank()) {
      writeLine()
    }
  }
}

private fun downloadResult(remoteFile: String,
                           localFile: Path,
                           ftpClient: SFTPClient) {
  spanBuilder("download file")
    .setAttribute("remoteFile", remoteFile)
    .setAttribute("localFile", localFile.toString())
    .use { span ->
      val localFileParent = localFile.parent
      val tempFile = localFileParent.resolve("${localFile.fileName}.download")
      Files.createDirectories(localFileParent)
      retryWithExponentialBackOff(action = { attempt ->
        Files.deleteIfExists(tempFile)
        Files.createFile(tempFile)
        ftpClient.get(remoteFile, NioFileDestination(tempFile))
        if (attempt != 1) {
          span.addEvent("file was downloaded", Attributes.of(
            AttributeKey.longKey("attemptNumber"), attempt.toLong(),
          ))
        }
      }, onException = { attempt, e ->
        span.addEvent("cannot download $remoteFile", Attributes.of(
          AttributeKey.longKey("attemptNumber"), attempt.toLong(),
          AttributeKey.stringKey("error"), e.toString(),
          AttributeKey.stringKey("remoteFile"), remoteFile,
        ))
        Files.deleteIfExists(tempFile)
      })
      Files.move(tempFile, localFile, StandardCopyOption.REPLACE_EXISTING)
    }
}

private val initLog by lazy {
  val root = Logger.getLogger("")
  if (root.handlers.isEmpty()) {
    root.level = Level.INFO
    root.addHandler(ConsoleHandler().also {
      it.formatter = object : Formatter() {
        override fun format(record: LogRecord): String {
          return record.message + System.lineSeparator()
        }
      }
    })
  }
}

private fun generateRemoteDirName(remoteDirPrefix: String): String {
  val currentDateTimeString = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME).replace(':', '-')
  return "$remoteDirPrefix-$currentDateTimeString-${java.lang.Long.toUnsignedString(random.nextLong(), Character.MAX_RADIX)}"
}

private fun AgentProxy.getAuthMethods(): List<AuthMethod> {
  val identities = identities
  System.getLogger("org.jetbrains.intellij.build.tasks.Sign")
    .info("SSH-Agent identities: ${identities.joinToString { String(it.comment, StandardCharsets.UTF_8) }}")
  return identities.map { AuthAgent(this, it) }
}

private fun getAgentConnector(): Connector? {
  try {
    return ConnectorFactory.getDefault().createConnector()
  }
  catch (ignored: AgentProxyException) {
    System.getLogger("org.jetbrains.intellij.build.tasks.Sign")
      .warn("SSH-Agent connector creation failed: ${ignored.message}")
  }
  return null
}

private inline fun executeTask(host: String,
                               user: String,
                               password: String,
                               remoteDirPrefix: String,
                               task: (ssh: SSHClient, sftp: SFTPClient, remoteDir: String) -> Unit) {
  initLog
  spanBuilder("connecting to $host").use { span ->
    retryWithExponentialBackOff(attempts = 10, action = {
      val config = DefaultConfig()
      config.keepAliveProvider = KeepAliveProvider.KEEP_ALIVE
      val ssh = PatchedSSHClient(config)
      ssh.addHostKeyVerifier(PromiscuousVerifier())
      ssh.connect(host)
      ssh
    }, onException = { attempt, e ->
      span.addEvent("cannot connect to $host", Attributes.of(
        AttributeKey.longKey("attemptNumber"), attempt.toLong(),
        AttributeKey.stringKey("error"), e.toString()
      ))
    })
  }.use { ssh ->
    val passwordFinder = object : PasswordFinder {
      override fun reqPassword(resource: Resource<*>?) = password.toCharArray().clone()
      override fun shouldRetry(resource: Resource<*>?) = false
    }
    val authMethods: List<AuthMethod> =
      (getAgentConnector()?.let { AgentProxy(it) }?.getAuthMethods() ?: emptyList()) +
      listOf(AuthPassword(passwordFinder), AuthKeyboardInteractive(PasswordResponseProvider(passwordFinder)))

    ssh.auth(user, authMethods)
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
  spanBuilder("remove remote dir").setAttribute("remoteDir", remoteDir).use {
    ssh.startSession().use { session ->
      val command = session.exec("rm -rf '$remoteDir'")
      command.join(5, TimeUnit.MINUTES)
      // must be called before checking exit code
      command.close()
      if (command.exitStatus != 0) {
        throw RuntimeException("cannot remove remote directory (exitStatus=${command.exitStatus}, " +
                               "exitErrorMessage=${command.exitErrorMessage})")
      }
    }
  }
}

private val macOsBinariesExtensions = setOf("jnilib", "dylib", "so", "tbd", "node")
private val zipArchivesExtensions = setOf("jar", "zip")

internal suspend fun recursivelySignMacBinaries(
  root: Path, context: BuildContext,
  executableFileMatchers: Collection<PathMatcher> = emptyList()
) = withContext(Dispatchers.IO) {
  val archives = mutableListOf<Path>()
  val binaries = mutableListOf<Path>()
  Files.walk(root).use { files ->
    files.forEach { file ->
      val relativePath = root.relativize(file)
      when {
        !file.isRegularFile() -> Unit
        file.extension in zipArchivesExtensions -> archives.add(file)
        file.extension in macOsBinariesExtensions ||
        executableFileMatchers.any { it.matches(relativePath) } ||
        SystemInfoRt.isUnix && PosixFilePermission.OWNER_EXECUTE in file.getPosixFilePermissions() -> {
          binaries.add(file)
        }
      }
    }
  }
  launch {
    signMacBinaries(context, binaries.filter {
      it.isMacOsBinary() && !it.isSigned()
    })
  }
  archives.forEach {
    launch {
      signAndRepackZipIfMacSignaturesAreMissing(it, context)
    }
  }
}

private suspend fun signAndRepackZipIfMacSignaturesAreMissing(zip: Path, context: BuildContext) = withContext(Dispatchers.IO) {
  val tempDir = context.paths.tempDir.resolve("${zip.name}-${UUID.randomUUID()}")
  val filesToBeSigned = mutableMapOf<String, Path>()
  readZipFile(zip) { name, entry ->
    val binary = entry.getData()
    if (binary.isMacOsBinary() && !runBlocking { binary.isSigned(name) }) {
      tempDir.createDirectories()
      val fileToBeSigned = tempDir.resolve(name.replace("/", "-"))
      fileToBeSigned.writeBytes(binary)
      filesToBeSigned[name] = fileToBeSigned
    }
  }
  signMacBinaries(context, filesToBeSigned.values.toList())
  try {
    if (filesToBeSigned.isNotEmpty()) {
      val repacked = tempDir.resolve(zip.name)
      repacked.copyZipReplacing(zip, filesToBeSigned)
      repacked.copyTo(zip, overwrite = true)
      zip.setLastModifiedTime(FileTime.from(context.options.buildDateInSeconds, TimeUnit.SECONDS))
    }
  }
  finally {
    filesToBeSigned.values.forEach(NioFiles::deleteRecursively)
  }
}

private fun Path.copyZipReplacing(origin: Path, entries: Map<String, Path>): Path {
  spanBuilder("Replacing unsigned entries in zip")
    .setAttribute("zip", "$origin")
    .setAttribute(AttributeKey.stringArrayKey("unsigned"), entries.keys.toList())
    .useWithScope {
      writeNewZip(this) { copy ->
        val index = PackageIndexBuilder()
        readZipFile(origin) { name, entry ->
          index.addFile(name)
          if (entries.contains(name)) {
            mapFileAndUse(entries.getValue(name)) { byteBuffer, _ ->
              copy.uncompressedData(name, byteBuffer)
            }
          }
          else {
            copy.uncompressedData(name, entry.getByteBuffer())
          }
        }
        index.writePackageIndex(copy)
      }
    }
  return this
}

private fun BuildContext.signingOptions(contentType: String): PersistentMap<String, String> {
  val certificateID = proprietaryBuildTools.macHostProperties?.codesignString
  check(certificateID != null || !signMacOsBinaries) {
    "Missing certificate ID"
  }
  val entitlements = paths.communityHomeDir.resolve("platform/build-scripts/tools/mac/scripts/entitlements.xml")
  check(entitlements.exists()) {
    "Missing $entitlements file"
  }
  return persistentMapOf(
    "mac_codesign_options" to "runtime",
    "mac_codesign_identity" to "$certificateID",
    "mac_codesign_entitlements" to "$entitlements",
    "mac_codesign_force" to "true", // true if omitted
    "contentType" to contentType
  )
}

internal suspend fun signMacBinaries(context: BuildContext, files: List<Path>, additionalOptions: Map<String, String> = emptyMap()) {
  withContext(Dispatchers.IO) {
    launch {
      signMacBinaries(context, files.filter {
        it.extension == "sit"
      }, contentType = "application/x-mac-app-zip", additionalOptions)
    }
    launch {
      signMacBinaries(context, files.filter {
        it.extension != "sit"
      }, contentType = "application/x-mac-app-bin", additionalOptions)
    }
  }
}

private suspend fun signMacBinaries(context: BuildContext, files: List<Path>,
                                    contentType: String, additionalOptions: Map<String, String>) {
  if (files.isEmpty()) return
  val permissions = if (SystemInfoRt.isUnix) {
    files.associateWith {
      HashSet(it.getPosixFilePermissions())
    }
  }
  else {
    emptyMap()
  }
  val span = spanBuilder("sign binaries for macOS distribution")
  span.setAttribute("contentType", contentType)
  span.setAttribute(AttributeKey.stringArrayKey("files"), files.map { it.name })
  val options = context.signingOptions(contentType).putAll(additionalOptions)
  context.executeStep(span, BuildOptions.MAC_SIGN_STEP) {
    context.signFiles(files, options)
    if (SystemInfoRt.isUnix) {
      // SRE-1223 workaround
      files.forEach {
        it.setPosixFilePermissions(permissions.getValue(it))
      }
    }
    val missingSignature = files.filter {
      it.isMacOsBinary() && !it.isSigned()
    }
    check(missingSignature.isEmpty()) {
      "Missing signature for:\n" + missingSignature.joinToString(separator = "\n\t")
    }
  }
}

private fun Path.isMacOsBinary(): Boolean = Files.newByteChannel(this@isMacOsBinary).isMacOsBinary()
internal fun Path.isMultiArchMacOsBinary(): Boolean {
  val (type, properties) = Files.newByteChannel(this@isMultiArchMacOsBinary).detectFileType()
  return type == FileType.MachO && properties.contains(FileProperties.MultiArch)
}

private fun ByteArray.isMacOsBinary(): Boolean = SeekableInMemoryByteChannel(this).isMacOsBinary()

private suspend fun ByteArray.isSigned(binaryId: String): Boolean =
  SeekableInMemoryByteChannel(this).isSigned(binaryId)

private suspend fun Path.isSigned(): Boolean = withContext(Dispatchers.IO) {
  Files.newByteChannel(this@isSigned).isSigned(this@isSigned.toString())
}

private fun SeekableByteChannel.isMacOsBinary(): Boolean = detectFileType().first == FileType.MachO
private fun SeekableByteChannel.detectFileType(): Pair<FileType, EnumSet<FileProperties>> = use {
  it.DetectFileType()
}

/**
 * Assumes [isMacOsBinary].
 */
private suspend fun SeekableByteChannel.isSigned(binaryId: String): Boolean = use {
  withContext(Dispatchers.IO) {
    val verificationParams = SignatureVerificationParams(
      signRootCertStore = null,
      timestampRootCertStore = null,
      buildChain = false,
      withRevocationCheck = false
    )
    val binaries = MachoArch(it).Extract()
    binaries.isNotEmpty() && binaries.all { binary ->
      val signatureData = try {
        binary.GetSignatureData()
      }
      catch (ignored: InvalidDataException) {
        return@all false
      }
      if (signatureData.IsEmpty) return@all false
      val signedMessage = try {
        SignedMessage.CreateInstance(signatureData)
      }
      catch (ignored: Exception) {
        // assuming Signature=adhoc
        return@all false
      }
      val result = try {
        val signedMessageVerifier = SignedMessageVerifier(SignatureVerificationLog(binaryId))
        signedMessageVerifier.VerifySignatureAsync(signedMessage, verificationParams)
      }
      catch (e: Exception) {
        throw Exception("Failed to verify $binaryId", e)
      }
      result.Status == VerifySignatureStatus.Valid
    }
  }
}

private class SignatureVerificationLog(val binaryId: String) : ILogger {
  val span: Span = Span.current()
  fun addEvent(str: String, category: String) {
    span.addEvent(str)
      .setAttribute("binary", binaryId)
      .setAttribute("category", category)
  }

  override fun Info(str: String) = addEvent(str, "INFO")
  override fun Warning(str: String) = addEvent(str, "WARN")
  override fun Error(str: String) = addEvent(str, "ERROR")
  override fun Trace(str: String) = Unit
}