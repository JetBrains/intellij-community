// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.PathUtilRt
import com.jcraft.jsch.agentproxy.AgentProxy
import com.jcraft.jsch.agentproxy.AgentProxyException
import com.jcraft.jsch.agentproxy.Connector
import com.jcraft.jsch.agentproxy.ConnectorFactory
import com.jcraft.jsch.agentproxy.sshj.AuthAgent
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import net.schmizz.keepalive.KeepAliveProvider
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.SSHException
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.userauth.method.AuthKeyboardInteractive
import net.schmizz.sshj.userauth.method.AuthMethod
import net.schmizz.sshj.userauth.method.AuthPassword
import net.schmizz.sshj.userauth.method.PasswordResponseProvider
import net.schmizz.sshj.userauth.password.PasswordFinder
import net.schmizz.sshj.userauth.password.Resource
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.impl.productInfo.ProductInfoValidator

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.security.SecureRandom
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.function.BiConsumer

import static com.intellij.openapi.util.Pair.pair

@CompileStatic
final class MacDmgBuilder {
  private final BuildContext buildContext
  private final AntBuilder ant
  private final String artifactsPath
  private final MacHostProperties macHostProperties
  private final String remoteDir
  private final MacDistributionCustomizer customizer
  private static final List<String> ENV_FOR_MAC_BUILDER = ['ARTIFACTORY_URL']

  private static final SecureRandom random = new SecureRandom()
  private static final ThreadPoolExecutor ioExecutor = Executors.newCachedThreadPool() as ThreadPoolExecutor

  private MacDmgBuilder(BuildContext buildContext,
                        MacDistributionCustomizer customizer,
                        String remoteDir,
                        MacHostProperties macHostProperties) {
    this.customizer = customizer
    this.buildContext = buildContext
    ant = buildContext.ant
    artifactsPath = buildContext.paths.artifacts
    this.macHostProperties = macHostProperties
    this.remoteDir = remoteDir
  }

  static void signBinaryFiles(BuildContext buildContext,
                              MacDistributionCustomizer customizer, MacHostProperties macHostProperties,
                              @NotNull Path macDistPath, @NotNull JvmArchitecture arch) {
    MacDmgBuilder dmgBuilder = createInstance(buildContext, customizer, macHostProperties)
    dmgBuilder
      .executeTask(dmgBuilder.macHostProperties.host, dmgBuilder.macHostProperties.userName, dmgBuilder.macHostProperties.password) {
        SSHClient ssh, SFTPClient sftp ->
          dmgBuilder.doSignBinaryFiles(macDistPath, arch, ssh, sftp)
      }
  }

  static void signAndBuildDmg(BuildContext buildContext, MacDistributionCustomizer customizer,
                              MacHostProperties macHostProperties, String macZipPath, String macAdditionalDirPath,
                              String jreArchivePath, String suffix, boolean notarize) {
    MacDmgBuilder dmgBuilder = createInstance(buildContext, customizer, macHostProperties)
    dmgBuilder.doSignAndBuildDmg(macZipPath, macAdditionalDirPath, jreArchivePath, suffix, notarize)
  }

  private static MacDmgBuilder createInstance(BuildContext buildContext,
                                              MacDistributionCustomizer customizer,
                                              MacHostProperties macHostProperties) {
    String currentDateTimeString = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME).replace(':', '-')
    long randomSeed = random.nextLong()
    String remoteDir = "intellij-builds/${buildContext.fullBuildNumber}-$currentDateTimeString-${Long.toUnsignedString(randomSeed, Character.MAX_RADIX)}"
    new MacDmgBuilder(buildContext, customizer, remoteDir, macHostProperties)
  }

  private void doSignBinaryFiles(@NotNull Path macDistPath,
                                 @NotNull JvmArchitecture arch,
                                 @NotNull SSHClient ssh,
                                 @NotNull SFTPClient sftp) {
    Path scripts = buildContext.paths.communityHomeDir.resolve("platform/build-scripts/tools/mac/scripts")
    uploadExecutable(sftp, scripts.resolve("signbin.sh"))

    Path signedFilesDir = Paths.get("$buildContext.paths.temp/signed-files")
    Files.createDirectories(signedFilesDir)

    List<String> failedToSign = []
    customizer.getBinariesToSign(buildContext, arch).each { relativePath ->
      buildContext.messages.progress("Signing $relativePath")
      Path fullPath = macDistPath.resolve(relativePath)
      uploadRegular(sftp, fullPath)
      FileUtil.delete(fullPath)
      String fileName = fullPath.fileName.toString()
      sshExec(ssh, "$remoteDir/signbin.sh \"$fileName\" ${macHostProperties.userName}" +
                   " ${macHostProperties.password} \"${this.macHostProperties.codesignString}\"", "signbin-${fileName}.log")

      Path file = signedFilesDir.resolve(fileName)
      download(sftp, file)
      if (Files.exists(file)) {
        Files.move(file, fullPath)
      }
      else {
        failedToSign << relativePath
      }
    }

    if (!failedToSign.empty) {
      buildContext.messages.error("Failed to sign files: $failedToSign")
    }
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  private void doSignAndBuildDmg(String macZipPath, String macAdditionalDirPath, String jreArchivePath, String suffix, boolean notarize) {
    String javaExePath = null
    if (jreArchivePath != null) {
      String rootDir = buildContext.bundledJreManager.jbrRootDir(new File(jreArchivePath)) ?: 'jdk'
      javaExePath = "../${rootDir}/Contents/Home/bin/java"
    }

    Path productJsonDir = buildContext.paths.tempDir.resolve("mac.dist.product-info.json.dmg$suffix")
    MacDistributionBuilder.generateProductJson(buildContext, productJsonDir, javaExePath)

    def zipRoot = MacDistributionBuilder.getZipRoot(buildContext, customizer)
    def installationDirectories = []
    def installationArchives = [pair(macZipPath, zipRoot)]
    if (macAdditionalDirPath != null) {
      installationDirectories += macAdditionalDirPath
    }
    if (jreArchivePath != null) {
      installationArchives += pair(jreArchivePath, "")
    }
    new ProductInfoValidator(buildContext).validateInDirectory(productJsonDir, "Resources/", installationDirectories, installationArchives)

    def targetName = buildContext.productProperties.getBaseArtifactName(buildContext.applicationInfo, buildContext.buildNumber) + suffix
    Path sitFile = Paths.get(artifactsPath, "${targetName}.sit")
    Files.copy(Paths.get(macZipPath), sitFile)
    ant.zip(destfile: sitFile.toString(), update: true) {
      zipfileset(dir: productJsonDir.toString(), prefix: zipRoot)

      if (macAdditionalDirPath != null) {
        zipfileset(dir: macAdditionalDirPath, prefix: zipRoot)
      }
    }

    def signMacArtifacts = !buildContext.options.buildStepsToSkip.contains(BuildOptions.MAC_SIGN_STEP)
    if (signMacArtifacts || !isMac()) {
      executeTask(this.macHostProperties.host, this.macHostProperties.userName, this.macHostProperties.password) {
        SSHClient ssh, SFTPClient sftp ->
          Path jreArchivePathPath = jreArchivePath != null ? Paths.get(jreArchivePath) : null
          signMacZip(sitFile, jreArchivePathPath, notarize, ssh, sftp)

          if (customizer.publishArchive) {
            buildContext.notifyArtifactBuilt(sitFile)
          }
          buildContext.executeStep("Build .dmg artifact for macOS", BuildOptions.MAC_DMG_STEP) {
            buildDmg(targetName, ssh, sftp)
          }
      }
    }
    else {
      if (jreArchivePath != null || signMacArtifacts) {
        bundleJBRAndSignSitLocally(sitFile.toFile(), jreArchivePath)
      }
      if (customizer.publishArchive) {
        buildContext.notifyArtifactBuilt(sitFile)
      }
      buildContext.executeStep("Build .dmg artifact for macOS", BuildOptions.MAC_DMG_STEP) {
        buildDmgLocally(sitFile.toFile(), targetName)
      }
    }
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  private void bundleJBRAndSignSitLocally(File targetFile, String jreArchivePath) {
    buildContext.messages.progress("Bundling JBR")
    File tempDir = new File(new File(buildContext.paths.temp, targetFile.getName()), "mac.dist.bundled.jre")
    tempDir.mkdirs()
    ant.copy(todir: tempDir) {
      ant.fileset(file: targetFile.path)
      if (jreArchivePath != null) {
        ant.fileset(file: jreArchivePath)
      }
    }
    ant.copy(todir: tempDir, file: "${buildContext.paths.communityHome}/platform/build-scripts/tools/mac/scripts/signapp.sh")
    ant.chmod(file: new File(tempDir, "signapp.sh"), perm: "777")
    List<String> args = [targetFile.name,
                         buildContext.fullBuildNumber,
                         "\"\"",
                         "\"\"",
                         "\"\"",
                         jreArchivePath != null ? '"' + PathUtilRt.getFileName(jreArchivePath) + '"' : "no-jdk",
                         "no",
                         customizer.bundleIdentifier,
    ]
    ant.exec(dir: tempDir, command: "./signapp.sh ${args.join(" ")}")
    ant.move(todir: artifactsPath, file: new File(tempDir, targetFile.name))
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  private void buildDmgLocally(File sitFile, String targetFileName) {
    File tempDir = new File(buildContext.paths.temp, "mac.dist.dmg")
    tempDir.mkdirs()
    buildContext.messages.progress("Building ${targetFileName}.dmg")
    def dmgImagePath = (buildContext.applicationInfo.isEAP ? customizer.dmgImagePathForEAP : null) ?: customizer.dmgImagePath
    def dmgImageCopy = "$tempDir/${buildContext.fullBuildNumber}.png"
    ant.copy(file: dmgImagePath, tofile: dmgImageCopy)
    ant.copy(file: sitFile, todir: tempDir)
    ant.copy(todir: tempDir) {
      ant.fileset(dir: "${buildContext.paths.communityHome}/platform/build-scripts/tools/mac/scripts") {
        include(name: "makedmg.sh")
        include(name: "create-dmg.sh")
        include(name: "makedmg-locally.sh")
      }
    }
    ant.chmod(file: new File(tempDir, "makedmg.sh"), perm: "777")

    ant.exec(dir: tempDir, command: "sh ./makedmg-locally.sh ${targetFileName} ${buildContext.fullBuildNumber}")
    def dmgFilePath = "$artifactsPath/${targetFileName}.dmg"
    ant.copy(tofile: dmgFilePath) {
      ant.fileset(dir: tempDir) {
        include(name: "**/${targetFileName}.dmg")
      }
    }
    if (!new File(dmgFilePath).exists()) {
      buildContext.messages.error("Failed to build .dmg file")
    }
    buildContext.notifyArtifactBuilt(dmgFilePath)
  }

  static boolean isMac() {
    final String osName = System.properties['os.name']
    return osName.toLowerCase().startsWith('mac')
  }

  private void buildDmg(@NotNull String targetFileName, @NotNull SSHClient ssh, @NotNull SFTPClient sftp) {
    buildContext.messages.progress("Building ${targetFileName}.dmg")
    Path tempDir = buildContext.paths.tempDir.resolve("files-for-dmg-$targetFileName")
    Files.createDirectories(tempDir)

    Path dmgImageCopy = tempDir.resolve("${buildContext.fullBuildNumber}.png")
    String dmgImagePath = (buildContext.applicationInfo.isEAP ? customizer.dmgImagePathForEAP : null) ?: customizer.dmgImagePath
    Files.copy(Paths.get(dmgImagePath), dmgImageCopy, StandardCopyOption.REPLACE_EXISTING)

    uploadRegular(sftp, dmgImageCopy)
    Path scripts = buildContext.paths.communityHomeDir.resolve("platform/build-scripts/tools/mac/scripts")
    uploadExecutable(sftp, scripts.resolve("makedmg.sh"))
    uploadExecutable(sftp, scripts.resolve("makedmg.py"))

    sshExec(ssh, "$remoteDir/makedmg.sh ${targetFileName} ${buildContext.fullBuildNumber}", "makedmg-${targetFileName}.log")

    Path dmgFilePath = Paths.get(artifactsPath, "${targetFileName}.dmg".toString())
    download(sftp, dmgFilePath)
    if (!Files.exists(dmgFilePath)) {
      buildContext.messages.error("Failed to build .dmg file")
    }
    buildContext.notifyArtifactBuilt(dmgFilePath)
  }

  private def signMacZip(Path targetFile, Path jreArchivePath, boolean notarize, SSHClient ssh, SFTPClient sftp) {
    buildContext.messages.block("Signing ${targetFile.fileName}") {
      buildContext.messages.progress("Uploading ${targetFile.fileName} to ${macHostProperties.host}")
      uploadRegular(sftp, targetFile)
      if (jreArchivePath != null) {
        uploadRegular(sftp, jreArchivePath)
      }
      def scripts = buildContext.paths.communityHomeDir.resolve("platform/build-scripts/tools/mac/scripts")
      uploadRegular(sftp, scripts.resolve("entitlements.xml"))
      uploadExecutable(sftp, scripts.resolve("sign.sh"))
      uploadExecutable(sftp, scripts.resolve("notarize.sh"))
      uploadExecutable(sftp, scripts.resolve("signapp.sh"))

      buildContext.messages.progress("Signing ${targetFile.fileName} on ${macHostProperties.host}")
      List<String> args = [targetFile.fileName.toString(),
                           buildContext.fullBuildNumber,
                           macHostProperties.userName,
                           macHostProperties.password,
                           "\"${macHostProperties.codesignString}\"".toString(),
                           jreArchivePath != null ? '"' + jreArchivePath.fileName.toString() + '"' : 'no-jdk',
                           notarize ? 'yes' : 'no',
                           customizer.bundleIdentifier,
      ]
      StringBuilder env = new StringBuilder();
      ENV_FOR_MAC_BUILDER.each {
        def value = System.getenv(it)
        if (value != null && !value.isEmpty()) {
          env.append(it).append('=').append(value).append(' ')
        }
      }
      sshExec(ssh, "${env.toString()}$remoteDir/signapp.sh ${args.join(" ")}", "signapp-${targetFile.fileName}.log")

      buildContext.messages.progress("Downloading signed ${targetFile.fileName} from ${macHostProperties.host}")
      Files.delete(targetFile)
      download(sftp, targetFile)
      if (!Files.exists(targetFile)) {
        buildContext.messages.error("Failed to sign ${targetFile.fileName}")
      }
    }
  }


  static CompletableFuture<?> runAsync(Runnable closure) {
    return CompletableFuture.runAsync(closure, ioExecutor)
  }

  private void sshExec(@NotNull SSHClient ssh, String commandString, String logFileName) {
    def logFile = buildContext.paths.logDir.resolve(logFileName)
    Files.createDirectories(logFile.parent)
    def session = ssh.startSession()
    try {
      def command = session.exec("$commandString 2>&1")
      try {
        CompletableFuture.allOf(
          runAsync { Files.copy(command.inputStream, logFile, StandardCopyOption.REPLACE_EXISTING) },
          runAsync { command.errorStream.transferTo(System.err) }
        ).get(6, TimeUnit.HOURS)

        command.join(1, TimeUnit.MINUTES)
      }
      catch (Exception e) {
        String logFileLocation = (Files.exists(logFile)) ? logFile.fileName.toString() : '<internal error - log file is not created>'
        throw new RuntimeException("SSH command failed, details are available in $logFileLocation: ${e.message}", e)
      }
      finally {
        if (Files.exists(logFile)) {
          buildContext.notifyArtifactBuilt(logFile)
        }
        command.close()
      }


      if (command.exitStatus != 0) {
        throw new RuntimeException("SSH command failed, details are available in ${logFile.fileName}" +
                                   " (exitStatus=${command.exitStatus}, exitErrorMessage=${command.exitErrorMessage})")
      }
    }
    finally {
      session.close()
    }
  }

  String uploadRegular(SFTPClient sftp, Path path) {
    String remote = "$remoteDir/${path.fileName}".toString()
    sftp.put(path.toString(), remote)
    return remote
  }

  String uploadExecutable(SFTPClient sftp, Path path) {
    String remote = uploadRegular(sftp, path)
    sftp.chmod(remote, 0777)
    return remote
  }

  void download(SFTPClient sftp, Path path) {
    sftp.get("$remoteDir/${path.fileName}".toString(), path.toString())
  }

  private void executeTask(String host, String username, String password, BiConsumer<SSHClient, SFTPClient> action) {
    def config = new DefaultConfig()
    config.keepAliveProvider = KeepAliveProvider.KEEP_ALIVE
    SSHClient ssh = new SSHClient(config)
    try {
      ssh.addHostKeyVerifier(new PromiscuousVerifier())
      ssh.connect(host)
      def passwordFinder = new PasswordFinder() {
        @Override
        char[] reqPassword(Resource<?> resource) {
          return password.toCharArray()
        }

        @Override
        boolean shouldRetry(Resource<?> resource) {
          return false
        }
      }
      List<AuthMethod> authMethods = new ArrayList()
      def connector = getAgentConnector()
      if (connector != null) {
        authMethods.addAll(getAuthMethods(new AgentProxy(connector)))
      }
      authMethods.add(new AuthPassword(passwordFinder))
      authMethods.add(new AuthKeyboardInteractive(new PasswordResponseProvider(passwordFinder)))

      ssh.auth(username, authMethods)
      ssh.newSFTPClient().withCloseable { SFTPClient sftp ->
        sftp.mkdir(remoteDir)
        try {
          action.accept(ssh, sftp)
        }
        finally {
          // as odd as it is, session can only be used once
          // https://stackoverflow.com/a/23467751
          removeDir(ssh, remoteDir)
        }
      }
    }
    catch (SSHException e) {
      buildContext.messages.error("SSH failed: $e.message", e)
      throw e
    }
    finally {
      ssh.close()
    }
  }

  private Connector getAgentConnector() {
    try {
      return ConnectorFactory.getDefault().createConnector()
    }
    catch (AgentProxyException ignored) {
      buildContext.messages.warning("SSH-Agent connector creation failed: ${ignored.message}")
    }
    return null
  }

  private List<AuthMethod> getAuthMethods(AgentProxy self) {
    def identities = self.identities
    buildContext.messages.info("SSH-Agent identities: ${identities.collect { new String(it.comment, StandardCharsets.UTF_8) }.join(",")}")
    def result = new ArrayList<AuthMethod>(identities.length)
    identities.each { result.add(new AuthAgent(self, it)) }
    return result
  }

  private static def removeDir(SSHClient ssh, String remoteDir) {
    ssh.startSession().withCloseable { session ->
      def command = session.exec("rm -rf '$remoteDir'")
      command.join(30, TimeUnit.SECONDS)
      // must be called before checking exit code
      command.close()
      if (command.exitStatus != 0) {
        throw new RuntimeException("cannot remove remote directory (exitStatus=${command.exitStatus}, " +
                                   "exitErrorMessage=${command.exitErrorMessage})")
      }
    }
  }
}