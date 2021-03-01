// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.SystemProperties
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.impl.productInfo.ProductInfoGenerator
import org.jetbrains.intellij.build.impl.productInfo.ProductInfoValidator

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

  String getDocTypes() {
    def iprAssociation = (customizer.associateIpr ? """
      <dict>
        <key>CFBundleTypeExtensions</key>
        <array>
          <string>ipr</string>
        </array>
        <key>CFBundleTypeIconFile</key>
        <string>$targetIcnsFileName</string>
        <key>CFBundleTypeName</key>
        <string>${buildContext.applicationInfo.productName} Project File</string>
        <key>CFBundleTypeRole</key>
        <string>Editor</string>
      </dict>
""" : "")
    def associations = ""
    for (FileAssociation fileAssociation : customizer.fileAssociations) {
        def iconFileName = targetIcnsFileName
        def iconFile = fileAssociation.iconPath
        if (!iconFile.isEmpty()) {
          iconFileName = iconFile.substring(iconFile.lastIndexOf(File.separator) + 1, iconFile.size())
        }
        associations += """<dict>
        <key>CFBundleTypeExtensions</key>
        <array>
"""
          associations += "          <string>${fileAssociation.extension}</string>\n"
          associations +=  """        </array>
        <key>CFBundleTypeRole</key>
        <string>Editor</string>
        <key>CFBundleTypeIconFile</key>
        <string>$iconFileName</string>        
      </dict>
"""
    }
    return iprAssociation + associations + customizer.additionalDocTypes
  }

  @Override
  void copyFilesForOsDistribution(@NotNull Path macDistPath, JvmArchitecture arch = null) {
    buildContext.messages.progress("Building distributions for $targetOs.osName")
    def docTypes = getDocTypes()
    Map<String, String> customIdeaProperties = [:]
    if (buildContext.productProperties.toolsJarRequired) {
      customIdeaProperties["idea.jre.check"] = "true"
    }
    customIdeaProperties.putAll(customizer.getCustomIdeaProperties(buildContext.applicationInfo))
    layoutMacApp(ideaProperties, customIdeaProperties, docTypes, macDistPath)
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
        buildContext.executeStep("Build .dmg artifact for macOS", BuildOptions.MAC_DMG_STEP) {
          boolean notarize = SystemProperties.getBooleanProperty("intellij.build.mac.notarize", true) &&
                             !SystemProperties.getBooleanProperty("build.is.personal", false)
          def jreManager = buildContext.bundledJreManager

          def tasks = new ArrayList<BuildTaskRunnable>()

          // With JRE
          if (buildContext.options.buildDmgWithBundledJre) {
            for (arch in [JvmArchitecture.x64, JvmArchitecture.aarch64]) {
              String suffix = (arch == JvmArchitecture.x64) ? "" : "-${arch.fileSuffix}"
              String archStr = arch.toString()

              def additional = buildContext.paths.tempDir.resolve("mac-additional-files-for-" + archStr)
              Files.createDirectories(additional)
              customizer.copyAdditionalFiles(buildContext, additional.toString(), arch)

              File jreArchive = jreManager.findJreArchive(OsFamily.MACOS, arch)
              if (jreArchive.file) {
                tasks.add(BuildTaskRunnable.task("dmg-" + archStr) { buildContext ->
                  buildContext.executeStep("Building dmg with JRE for " + archStr, "mac_dmg_jre_" + archStr) {
                    MacDmgBuilder.signAndBuildDmg(buildContext, customizer, buildContext.proprietaryBuildTools.macHostProperties, macZipPath,
                                                  additional.toString(), jreArchive.absolutePath, suffix, notarize)
                  }
                })
              }
              else {
                buildContext.messages.info("Skipping building macOS distribution for $archStr with bundled JRE because JRE archive is missing")
              }
            }
          }

          // Without JRE
          if (buildContext.options.buildDmgWithoutBundledJre) {
            tasks.add(BuildTaskRunnable.task("dmg-no-jdk") { buildContext ->
              buildContext.executeStep("Building dmg without JRE", "mac_dmg_no_jre") {
                MacDmgBuilder.signAndBuildDmg(buildContext, customizer, buildContext.proprietaryBuildTools.macHostProperties, macZipPath,
                                              null, null, "-no-jdk", notarize)
              }
            })
          }

          BuildTasksImpl.runInParallel(tasks, buildContext)
          FileUtil.delete(Paths.get(macZipPath))
        }
      }
    }
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  private void layoutMacApp(@NotNull Path ideaPropertiesFile, Map<String, String> customIdeaProperties, String docTypes, @NotNull Path macDistPath) {
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

    //todo[nik] don't mix properties for idea.properties file with properties for Info.plist
    Map<String, String> properties = readIdeaProperties(ideaPropertiesFile, customIdeaProperties)
    properties["idea.vendor.name"] = buildContext.applicationInfo.shortCompanyName

    def coreKeys = ["idea.platform.prefix", "idea.paths.selector", "idea.executable", "idea.vendor.name"]

    String coreProperties = submapToXml(properties, coreKeys)

    StringBuilder effectiveProperties = new StringBuilder()
    properties.each { k, v ->
      if (!coreKeys.contains(k)) {
        effectiveProperties.append("$k=$v\n")
      }
    }

    new File("$target/bin/idea.properties").text = effectiveProperties.toString()
    // todo support aarch64
    def vmOptions = VmOptionsGenerator.computeVmOptions(JvmArchitecture.x64, buildContext.applicationInfo.isEAP, buildContext.productProperties)
    //todo[r.sh] additional VM options should go into the launcher (probably via Info.plist)
    vmOptions += buildContext.productProperties.additionalIdeJvmArguments.split(' ').toList()
    vmOptions += ["-XX:ErrorFile=\$USER_HOME/java_error_in_${executable}_%p.log", "-XX:HeapDumpPath=\$USER_HOME/java_error_in_${executable}.hprof"]
    new File("$target/bin/${executable}.vmoptions").text = vmOptions.join('\n') + '\n'

    String classPath = buildContext.bootClassPathJarNames.collect { "\$APP_PACKAGE/Contents/lib/${it}" }.join(":")

    String archsString = """
    <key>LSArchitecturePriority</key>
    <array>"""
    macCustomizer.architectures.each {
      archsString += "<string>$it</string>"
    }
    archsString += "</array>\n"

    List<String> urlSchemes = macCustomizer.urlSchemes
    String urlSchemesString = ""
    if (urlSchemes.size() > 0) {
      urlSchemesString += """
      <key>CFBundleURLTypes</key>
      <array>
        <dict>
          <key>CFBundleTypeRole</key>
          <string>Editor</string>
          <key>CFBundleURLName</key>
          <string>Stacktrace</string>
          <key>CFBundleURLSchemes</key>
          <array>
"""
      urlSchemes.each { scheme ->
        urlSchemesString += "            <string>${scheme}</string>"
      }
      urlSchemesString += """
          </array>
        </dict>
      </array>
"""
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
      replacefilter(token: "@@archs@@", value: archsString)
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
        allPaths.each {
          zipfileset(dir: it, prefix: zipRoot) {
            executableFilePatterns.each {
              exclude(name: it)
            }
            exclude(name: "*.txt")
          }
        }

        allPaths.each {
          zipfileset(dir: it, filemode: "755", prefix: zipRoot) {
            executableFilePatterns.each {
              include(name: it)
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


  private static String submapToXml(Map<String, String> properties, List<String> keys) {
// generate properties description for Info.plist
    StringBuilder buff = new StringBuilder()

    keys.each { key ->
      String value = properties[key]
      if (value != null) {
        String string =
          """
        <key>$key</key>
        <string>$value</string>
"""
        buff.append(string)
      }
    }
    return buff.toString()
  }

  /**
   * E.g.
   *
   * Load all properties from file:
   *    readIdeaProperties(buildContext, "$home/ruby/build/idea.properties")
   *
   * Load all properties except "idea.cycle.buffer.size", change "idea.max.intellisense.filesize" to 3000
   * and enable "idea.is.internal" mode:
   *    readIdeaProperties(buildContext, "$home/ruby/build/idea.properties",
   *                       "idea.properties" : ["idea.max.intellisense.filesize" : 3000,
   *                                           "idea.cycle.buffer.size" : null,
   *                                           "idea.is.internal" : true ])
   * @param args
   * @return text xml properties description in xml
   */
  private Map<String, String> readIdeaProperties(@NotNull Path propertiesFile, Map<String, String> customProperties = [:]) {
    Map<String, String> ideaProperties = [:]
    Files.newBufferedReader(propertiesFile).withCloseable {
      Properties loadedProperties = new Properties()
      loadedProperties.load(it)
      ideaProperties.putAll(loadedProperties as Map<String, String>)
    }

    Map<String, String> properties =
      ["CVS_PASSFILE"                          : "~/.cvspass",
       "com.apple.mrj.application.live-resize" : "false",
       "idea.paths.selector"                   : buildContext.systemSelector,
       "idea.executable"                       : buildContext.productProperties.baseFileName,
       "java.endorsed.dirs"                    : "",
       "idea.smooth.progress"                  : "false",
       "apple.laf.useScreenMenuBar"            : "true",
       "apple.awt.fileDialogForDirectories"    : "true",
       "apple.awt.graphics.UseQuartz"          : "true",
       "apple.awt.fullscreencapturealldisplays": "false"]
    if (buildContext.productProperties.platformPrefix != null) {
      properties["idea.platform.prefix"] = buildContext.productProperties.platformPrefix
    }

    properties += customProperties

    properties.each { k, v ->
      if (v == null) {
        // if overridden with null - ignore property
        ideaProperties.remove(k)
      }
      else {
        // if property is overridden in args map - use new value
        ideaProperties[k] = v
      }
    }

    return ideaProperties
  }
}
