// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.SystemProperties
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.impl.productInfo.ProductInfoGenerator
import org.jetbrains.intellij.build.impl.productInfo.ProductInfoValidator

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDate

@CompileStatic
final class MacDistributionBuilder extends OsSpecificDistributionBuilder {
  private final MacDistributionCustomizer customizer
  private final Path ideaProperties
  private final String targetIcnsFileName

  MacDistributionBuilder(BuildContext buildContext, MacDistributionCustomizer customizer, Path ideaProperties) {
    super(buildContext)
    this.ideaProperties = ideaProperties
    this.customizer = customizer
    targetIcnsFileName = "${buildContext.productProperties.baseFileName}.icns"
  }

  @Override
  OsFamily getTargetOs() {
    return OsFamily.MACOS
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  String getDocTypes() {
    List<String> associations = []

    if (customizer.associateIpr) {
      String association = """<dict>
        <key>CFBundleTypeExtensions</key>
        <array>
          <string>ipr</string>
        </array>
        <key>CFBundleTypeIconFile</key>
        <string>${targetIcnsFileName}</string>
        <key>CFBundleTypeName</key>
        <string>${buildContext.applicationInfo.productName} Project File</string>
        <key>CFBundleTypeRole</key>
        <string>Editor</string>
      </dict>"""
      associations.add(association)
    }

    for (FileAssociation fileAssociation : customizer.fileAssociations) {
      String iconPath = fileAssociation.iconPath
      String association = """<dict>
        <key>CFBundleTypeExtensions</key>
        <array>
          <string>${fileAssociation.extension}</string>
        </array>
        <key>CFBundleTypeIconFile</key>
        <string>${iconPath.isEmpty() ? targetIcnsFileName : new File(iconPath).name}</string>        
        <key>CFBundleTypeRole</key>
        <string>Editor</string>
      </dict>"""
      associations.add(association)
    }

    return associations.join('\n      ') + customizer.additionalDocTypes
  }

  @Override
  void copyFilesForOsDistribution(@NotNull Path macDistPath, JvmArchitecture arch = null) {
    buildContext.messages.progress("Building distributions for $targetOs.osName")

    List<String> platformProperties = [
      "\n#---------------------------------------------------------------------",
      "# macOS-specific system properties",
      "#---------------------------------------------------------------------",
      "com.apple.mrj.application.live-resize=false",
      "apple.laf.useScreenMenuBar=true",
      "jbScreenMenuBar.enabled=true",
      "apple.awt.fileDialogForDirectories=true",
      "apple.awt.graphics.UseQuartz=true",
      "apple.awt.fullscreencapturealldisplays=false"
    ]
    customizer.getCustomIdeaProperties(buildContext.applicationInfo).each {k,v ->
      platformProperties.add(k + '=' + v)
    }

    def docTypes = getDocTypes()

    layoutMacApp(ideaProperties, platformProperties, docTypes, macDistPath)

    BuildTasksImpl.unpackPty4jNative(buildContext, macDistPath, "darwin")

    BuildTasksImpl.generateBuildTxt(buildContext, macDistPath.resolve("Resources"))
    BuildTasksImpl.copyDistFiles(buildContext, macDistPath)

    customizer.copyAdditionalFiles(buildContext, macDistPath.toString())
    if (arch != null) {
      customizer.copyAdditionalFiles(buildContext, macDistPath.toString(), arch)
    }

    if (!customizer.binariesToSign.empty) {
      if (buildContext.proprietaryBuildTools.macHostProperties == null) {
        buildContext.messages.info("A macOS build agent isn't configured, binary files won't be signed")
      }
      else {
        buildContext.executeStep("Sign binaries for macOS distribution", BuildOptions.MAC_SIGN_STEP) {
          MacDmgBuilder.signBinaryFiles(buildContext, customizer, buildContext.proprietaryBuildTools.macHostProperties, macDistPath)
        }
      }
    }
  }

  @Override
  void buildArtifacts(@NotNull Path osSpecificDistPath) {
    copyFilesForOsDistribution(osSpecificDistPath)
    buildContext.executeStep("Build macOS artifacts", BuildOptions.MAC_ARTIFACTS_STEP) {
      def macZipPath = buildMacZip(osSpecificDistPath)
      if (buildContext.proprietaryBuildTools.macHostProperties == null) {
        buildContext.messages.info("A macOS build agent isn't configured - .dmg artifact won't be produced")
        buildContext.notifyArtifactBuilt(macZipPath)
      }
      else {
        boolean notarize = SystemProperties.getBooleanProperty("intellij.build.mac.notarize", true) &&
                           !SystemProperties.getBooleanProperty("build.is.personal", false)
        def jreManager = buildContext.bundledJreManager

        def tasks = new ArrayList<BuildTaskRunnable>()

        for (arch in [JvmArchitecture.x64, JvmArchitecture.aarch64]) {
          String suffix = (arch == JvmArchitecture.x64) ? "" : "-${arch.fileSuffix}"
          String archStr = arch.toString()
          // With JRE
          if (buildContext.options.buildDmgWithBundledJre) {
            def additional = buildContext.paths.tempDir.resolve("mac-additional-files-for-" + archStr)
            Files.createDirectories(additional)
            customizer.copyAdditionalFiles(buildContext, additional.toString(), arch)

            File jreArchive = jreManager.findJreArchive(OsFamily.MACOS, arch)
            if (jreArchive.file) {
              tasks.add(BuildTaskRunnable.task("dmg-" + archStr) { buildContext ->
                buildContext.executeStep("Building dmg with JRE for " + archStr, "${BuildOptions.MAC_ARTIFACTS_STEP}_jre_$archStr") {
                  MacDmgBuilder.signAndBuildDmg(buildContext, customizer, buildContext.proprietaryBuildTools.macHostProperties, macZipPath,
                                                additional.toString(), jreArchive.absolutePath, suffix, notarize)
                }
              })
            }
            else {
              buildContext.messages.info(
                "Skipping building macOS distribution for $archStr with bundled JRE because JRE archive is missing")
            }
          }

          // Without JRE
          if (buildContext.options.buildDmgWithoutBundledJre) {
            tasks.add(BuildTaskRunnable.task("dmg-no-jdk-" + archStr) { buildContext ->
              buildContext.executeStep("Building dmg without JRE for " + archStr, "${BuildOptions.MAC_ARTIFACTS_STEP}_no_jre_$archStr") {
                MacDmgBuilder.signAndBuildDmg(buildContext, customizer, buildContext.proprietaryBuildTools.macHostProperties, macZipPath,
                                              null, null, "-no-jdk$suffix", notarize)
              }
            })
          }
        }

        BuildTasksImpl.runInParallel(tasks, buildContext)
        FileUtil.delete(Paths.get(macZipPath))
      }
    }
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  private void layoutMacApp(Path ideaPropertiesFile, List<String> platformProperties, String docTypes, Path macDistPath) {
    String target = macDistPath.toString()
    def macCustomizer = customizer
    buildContext.ant.copy(todir: "$target/bin") {
      fileset(dir: "$buildContext.paths.communityHome/bin/mac")
    }

    buildContext.ant.copy(todir: target) {
      fileset(dir: "$buildContext.paths.communityHome/platform/build-scripts/resources/mac/Contents")
    }

    String executable = buildContext.productProperties.baseFileName
    buildContext.ant.move(file: "$target/MacOS/executable", tofile: "$target/MacOS/$executable")

    String icnsPath = (buildContext.applicationInfo.isEAP ? customizer.icnsPathForEAP : null) ?: customizer.icnsPath
    buildContext.ant.copy(file: icnsPath, tofile: "$target/Resources/$targetIcnsFileName")

    customizer.fileAssociations.each {
      if (!it.iconPath.empty) {
        buildContext.ant.copy(file: it.iconPath, todir: "$target/Resources", overwrite: "true")
      }
    }

    String fullName = buildContext.applicationInfo.productName

    //todo[nik] improve
    String minor = buildContext.applicationInfo.minorVersion
    boolean isNotRelease = buildContext.applicationInfo.isEAP && !minor.contains("RC") && !minor.contains("Beta")
    String version = isNotRelease ? "EAP $buildContext.fullBuildNumber" : "${buildContext.applicationInfo.majorVersion}.${minor}"
    String EAP = isNotRelease ? "-EAP" : ""

    List<String> properties = Files.readAllLines(ideaPropertiesFile)
    properties += platformProperties
    Files.write(macDistPath.resolve("bin/idea.properties"), properties)

    List<String> fileVmOptions = VmOptionsGenerator.computeVmOptions(buildContext.applicationInfo.isEAP, buildContext.productProperties)
    List<String> launcherVmOptions = buildContext.additionalJvmArguments
    //todo[r.sh] support arbitrary JVM options in the launcher
    List<String> nonProperties = launcherVmOptions.findAll { !it.startsWith('-D') }
    if (!nonProperties.isEmpty()) {
      fileVmOptions.addAll(nonProperties)
      launcherVmOptions.removeAll(nonProperties)
    }

    fileVmOptions.add("-XX:ErrorFile=\$USER_HOME/java_error_in_${executable}_%p.log")
    fileVmOptions.add("-XX:HeapDumpPath=\$USER_HOME/java_error_in_${executable}.hprof")
    Files.writeString(macDistPath.resolve("bin/${executable}.vmoptions"), String.join('\n', fileVmOptions) + '\n', StandardCharsets.US_ASCII)

    String coreProperties = propertiesToXml(launcherVmOptions, ['idea.executable': buildContext.productProperties.baseFileName])

    String classPath = buildContext.bootClassPathJarNames.collect { "\$APP_PACKAGE/Contents/lib/${it}" }.join(":")

    String archString = '<key>LSArchitecturePriority</key>\n    <array>\n'
    macCustomizer.architectures.each {archString += '      <string>' + it + '</string>\n' }
    archString += '    </array>'

    List<String> urlSchemes = macCustomizer.urlSchemes
    String urlSchemesString = ""
    if (urlSchemes.size() > 0) {
      urlSchemesString += '''<key>CFBundleURLTypes</key>
    <array>
      <dict>
        <key>CFBundleTypeRole</key>
        <string>Editor</string>
        <key>CFBundleURLName</key>
        <string>Stacktrace</string>
        <key>CFBundleURLSchemes</key>
        <array>
'''
      urlSchemes.each {urlSchemesString += '          <string>' + it + '</string>\n' }
      urlSchemesString += '''\
        </array>
      </dict>
    </array>'''
    }
    String todayYear = LocalDate.now().year
    buildContext.ant.replace(file: "$target/Info.plist") {
      replacefilter(token: "@@build@@", value: buildContext.fullBuildNumber)
      replacefilter(token: "@@doc_types@@", value: docTypes ?: "")
      replacefilter(token: "@@executable@@", value: executable)
      replacefilter(token: "@@icns@@", value: targetIcnsFileName)
      replacefilter(token: "@@bundle_name@@", value: fullName)
      replacefilter(token: "@@product_state@@", value: EAP)
      replacefilter(token: "@@bundle_identifier@@", value: macCustomizer.bundleIdentifier)
      replacefilter(token: "@@year@@", value: "$todayYear")
      replacefilter(token: "@@company_name@@", value: buildContext.applicationInfo.companyName)
      replacefilter(token: "@@min_year@@", value: "2000")
      replacefilter(token: "@@max_year@@", value: "$todayYear")
      replacefilter(token: "@@version@@", value: version)
      replacefilter(token: "@@idea_properties@@", value: coreProperties)
      replacefilter(token: "@@class_path@@", value: classPath)
      replacefilter(token: "@@url_schemes@@", value: urlSchemesString)
      replacefilter(token: "@@architectures@@", value: archString)
      replacefilter(token: "@@min_osx@@", value: macCustomizer.minOSXVersion)
    }

    Path distBinDir = macDistPath.resolve("bin")

    buildContext.ant.copy(todir: distBinDir.toString()) {
      fileset(dir: "$buildContext.paths.communityHome/platform/build-scripts/resources/mac/scripts")
      filterset(begintoken: "@@", endtoken: "@@") {
        filter(token: "product_full", value: fullName)
        filter(token: "script_name", value: executable)
      }
    }

    BuildTasksImpl.copyInspectScript(buildContext, distBinDir)

    buildContext.ant.fixcrlf(srcdir: distBinDir.toString(), includes: "*.sh", eol: "unix")
    buildContext.ant.fixcrlf(srcdir: distBinDir.toString(), includes: "*.py", eol: "unix")
  }

  @Override
  List<String> generateExecutableFilesPatterns(boolean includeJre) {
    [
      "bin/*.sh",
      "bin/*.py",
      "bin/fsnotifier",
      "bin/printenv",
      "bin/restarter",
      "MacOS/*"
    ] + customizer.extraExecutables
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  private String buildMacZip(@NotNull Path macDistPath) {
    return buildContext.messages.block("Build .zip archive for macOS") {
      List<String> allPaths = [buildContext.paths.distAll, macDistPath.toString()]
      def zipRoot = getZipRoot(buildContext, customizer)
      def baseName = buildContext.productProperties.getBaseArtifactName(buildContext.applicationInfo, buildContext.buildNumber)
      def targetPath = "${buildContext.paths.artifacts}/${baseName}.mac.zip"
      buildContext.messages.progress("Building zip archive for macOS")

      Path productJsonDir = buildContext.paths.tempDir.resolve("mac.dist.product-info.json.zip")
      generateProductJson(buildContext, productJsonDir, null)
      allPaths.add(productJsonDir.toString())

      def executableFilePatterns = generateExecutableFilesPatterns(false)
      buildContext.ant.zip(zipfile: targetPath) {
        allPaths.each {path ->
          zipfileset(dir: path, prefix: zipRoot) {
            executableFilePatterns.each { pattern ->
              exclude(name: pattern)
            }
            exclude(name: "*.txt")
          }
        }

        allPaths.each { path ->
          zipfileset(dir: path, filemode: "755", prefix: zipRoot) {
            executableFilePatterns.each { pattern ->
              include(name: pattern)
            }
          }
        }

        // the root directory must not have files other than Info.plist so files like NOTICE.TXT and LICENSE.txt are copied to Resources subfolder
        //todo specify paths to such files in ProductProperties and put them to dist.mac/Resources to get rid of this exclusion
        //todo remove code which does the same from tools/mac/scripts/signapp.sh
        zipfileset(dir: buildContext.paths.distAll, prefix: "$zipRoot/Resources") {
          include(name: "*.txt")
        }
      }

      ProductInfoValidator.checkInArchive(buildContext, targetPath, "$zipRoot/Resources")
      return targetPath
    }
  }

  static String getZipRoot(BuildContext buildContext, MacDistributionCustomizer customizer) {
    "${customizer.getRootDirectoryName(buildContext.applicationInfo, buildContext.buildNumber)}/Contents"
  }

  static void generateProductJson(BuildContext buildContext, Path productJsonDir, String javaExecutablePath) {
    String executable = buildContext.productProperties.baseFileName
    new ProductInfoGenerator(buildContext).generateProductJson(productJsonDir.resolve("Resources"), "../bin", null,
                                                               "../MacOS/${executable}", javaExecutablePath,
                                                               "../bin/${executable}.vmoptions", OsFamily.MACOS)
  }

  @CompileStatic
  private static String propertiesToXml(List<String> properties, Map<String, String> moreProperties) {
    StringBuilder buff = new StringBuilder()
    properties.each { it ->
      int p = it.indexOf('=')
      buff.append('        <key>').append(it.substring(2, p)).append('</key>\n')
      buff.append('        <string>').append(it.substring(p + 1)).append('</string>\n')
    }
    moreProperties.each { key, value ->
      buff.append('        <key>').append(key).append('</key>\n')
      buff.append('        <string>').append(value).append('</string>\n')
    }
    return buff.toString().trim()
  }
}
