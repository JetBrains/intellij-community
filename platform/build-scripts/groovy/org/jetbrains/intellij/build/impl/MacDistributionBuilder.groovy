/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.intellij.build.impl

import com.intellij.util.PathUtilRt
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.MacDistributionCustomizer

import java.time.LocalDate

/**
 * @author nik
 */
class MacDistributionBuilder extends OsSpecificDistributionBuilder {
  private final MacDistributionCustomizer customizer
  private final File ideaProperties
  private final String icnsPath

  MacDistributionBuilder(BuildContext buildContext, MacDistributionCustomizer customizer, File ideaProperties) {
    super(BuildOptions.OS_MAC, "Mac OS", buildContext)
    this.ideaProperties = ideaProperties
    this.customizer = customizer
    icnsPath = (buildContext.applicationInfo.isEAP ? customizer.icnsPathForEAP : null) ?: customizer.icnsPath
  }

  @Override
  String copyFilesForOsDistribution() {
    buildContext.messages.progress("Building distributions for Mac OS")
    String macDistPath = "$buildContext.paths.buildOutputRoot/dist.mac"
    def docTypes = (customizer.associateIpr ? """
      <dict>
        <key>CFBundleTypeExtensions</key>
        <array>
          <string>ipr</string>
        </array>
        <key>CFBundleTypeIconFile</key>
        <string>${PathUtilRt.getFileName(icnsPath)}</string>
        <key>CFBundleTypeName</key>
        <string>${buildContext.applicationInfo.productName} Project File</string>
        <key>CFBundleTypeRole</key>
        <string>Editor</string>
      </dict>
""" : "") + customizer.additionalDocTypes
    Map<String, String> customIdeaProperties = [:]
    if (buildContext.productProperties.toolsJarRequired) {
      customIdeaProperties["idea.jre.check"] = "true"
    }
    customIdeaProperties.putAll(customizer.getCustomIdeaProperties(buildContext.applicationInfo))
    layoutMacApp(ideaProperties, customIdeaProperties, docTypes, macDistPath)

    if (customizer.helpId != null) {
      def helpZip = customizer.getPathToHelpZip(buildContext)
      if (helpZip == null) {
        buildContext.messages.error("Path to zip archive with help files isn't specified")
      }
      if (!new File(helpZip).exists() && buildContext.options.isInDevelopmentMode) {
        buildContext.messages.warning("Help won't be bundled with Mac OS X distribution: $helpZip doesn't exist")
      }
      else {
        buildContext.ant.unzip(src: helpZip, dest: "$macDistPath/Resources")
      }
    }

    customizer.copyAdditionalFiles(buildContext, macDistPath)

    if (!customizer.binariesToSign.empty) {
      if (buildContext.proprietaryBuildTools.macHostProperties == null) {
        buildContext.messages.info("A Mac OS build agent isn't configured, binary files won't be signed")
      }
      else {
        buildContext.executeStep("Sign binaries for Mac OS distribution", BuildOptions.MAC_SIGN_STEP) {
          MacDmgBuilder.signBinaryFiles(buildContext, customizer, buildContext.proprietaryBuildTools.macHostProperties, macDistPath)
        }
      }
    }
    return macDistPath
  }

  @Override
  void buildArtifacts(String osSpecificDistPath) {
    buildContext.executeStep("Build artifacts for Mac OS X", BuildOptions.MAC_ARTIFACTS) {
      def macZipPath = buildMacZip(osSpecificDistPath)
      if (buildContext.proprietaryBuildTools.macHostProperties == null) {
        buildContext.messages.info("A Mac OS build agent isn't configured, dmg artifact won't be produced")
        buildContext.notifyArtifactBuilt(macZipPath)
      }
      else {
        buildContext.executeStep("Build dmg artifact for Mac OS X", BuildOptions.MAC_DMG_STEP) {
          MacDmgBuilder.signAndBuildDmg(buildContext, customizer, buildContext.proprietaryBuildTools.macHostProperties, macZipPath)
          buildContext.ant.delete(file: macZipPath)
        }
      }
    }
  }

  private void layoutMacApp(File ideaPropertiesFile, Map<String, String> customIdeaProperties, String docTypes, String macDistPath) {
    String target = macDistPath
    def macCustomizer = customizer
    buildContext.ant.copy(todir: "$target/bin") {
      fileset(dir: "$buildContext.paths.communityHome/bin/mac")
      if (buildContext.productProperties.yourkitAgentBinariesDirectoryPath != null) {
        fileset(dir: buildContext.productProperties.yourkitAgentBinariesDirectoryPath) {
          include(name: "libyjpagent.jnilib")
        }
      }
    }
    buildContext.ant.copy(todir: "$target/lib/libpty/macosx") {
      fileset(dir: "$buildContext.paths.communityHome/lib/libpty/macosx")
    }

    buildContext.ant.copy(todir: target) {
      fileset(dir: "$buildContext.paths.communityHome/platform/build-scripts/resources/mac/Contents")
    }

    String executable = buildContext.productProperties.baseFileName
    buildContext.ant.move(file: "$target/MacOS/executable", tofile: "$target/MacOS/$executable")

    buildContext.ant.copy(file: icnsPath, todir: "$target/Resources")
    String helpId = macCustomizer.helpId
    if (helpId != null) {
      String helpIcns = "$target/Resources/${helpId}.help/Contents/Resources/Shared/product.icns"
      buildContext.ant.copy(file: icnsPath, tofile: helpIcns)
    }

    String fullName = buildContext.applicationInfo.productName

    //todo[nik] improve
    String minor = buildContext.applicationInfo.minorVersion
    boolean isNotRelease = buildContext.applicationInfo.isEAP && !minor.contains("RC") && !minor.contains("Beta")
    String version = isNotRelease ? "EAP $buildContext.fullBuildNumber" : "${buildContext.applicationInfo.majorVersion}.${minor}"
    String EAP = isNotRelease ? "-EAP" : ""

    //todo[nik] don't mix properties for idea.properties file with properties for Info.plist
    Map<String, String> properties = readIdeaProperties(ideaPropertiesFile, customIdeaProperties)

    def coreKeys = ["idea.platform.prefix", "idea.paths.selector", "idea.executable"]

    String coreProperties = submapToXml(properties, coreKeys)

    StringBuilder effectiveProperties = new StringBuilder()
    properties.each { k, v ->
      if (!coreKeys.contains(k)) {
        effectiveProperties.append("$k=$v\n")
      }
    }

    new File("$target/bin/idea.properties").text = effectiveProperties.toString()
    String ideaVmOptions = "${VmOptionsGenerator.vmOptionsForArch(JvmArchitecture.x64, buildContext.productProperties)} -XX:+UseCompressedOops -Dfile.encoding=UTF-8 ${VmOptionsGenerator.computeCommonVmOptions(buildContext.applicationInfo.isEAP)} -Xverify:none ${buildContext.productProperties.additionalIdeJvmArguments} -XX:ErrorFile=\$USER_HOME/java_error_in_${executable}_%p.log -XX:HeapDumpPath=\$USER_HOME/java_error_in_${executable}.hprof -Xbootclasspath/a:../lib/boot.jar".trim()
    if (buildContext.applicationInfo.isEAP && buildContext.productProperties.enableYourkitAgentInEAP && macCustomizer.enableYourkitAgentInEAP) {
      ideaVmOptions += " " + VmOptionsGenerator.yourkitOptions(buildContext.systemSelector, "")
    }
    new File("$target/bin/${executable}.vmoptions").text = ideaVmOptions.split(" ").join("\n")

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
    String bundledHelpAttributes
    if (helpId != null) {
      bundledHelpAttributes = """
        <key>CFBundleHelpBookName</key>
        <string>JetBrains.${helpId}.help</string>
        <key>CFBundleHelpBookFolder</key>
        <string>${helpId}.help</string>
"""
    }
    else {
      bundledHelpAttributes = ""
    }

    String todayYear = LocalDate.now().year
    buildContext.ant.replace(file: "$target/Info.plist") {
      replacefilter(token: "@@build@@", value: buildContext.fullBuildNumber)
      replacefilter(token: "@@doc_types@@", value: docTypes ?: "")
      replacefilter(token: "@@executable@@", value: executable)
      replacefilter(token: "@@icns@@", value: PathUtilRt.getFileName(icnsPath))
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
      replacefilter(token: "@@help_id@@", value: helpId)
      replacefilter(token: "@@url_schemes@@", value: urlSchemesString)
      replacefilter(token: "@@archs@@", value: archsString)
      replacefilter(token: "@@min_osx@@", value: macCustomizer.minOSXVersion)
      replacefilter(token: "@@bundled_help_attributes@@", value: bundledHelpAttributes)
    }

    buildContext.ant.replace(dir: "$target/bin", includes: "inspect.sh,format.sh") {
      replacefilter(token: "@@product_full@@", value: fullName)
      replacefilter(token: "@@script_name@@", value: executable)
    }
    String inspectScript = buildContext.productProperties.inspectCommandName
    if (inspectScript != "inspect") {
      String targetPath = "$target/bin/${inspectScript}.sh"
      buildContext.ant.move(file: "$target/bin/inspect.sh", tofile: targetPath)
      buildContext.patchInspectScript(targetPath)
    }

    buildContext.ant.fixcrlf(srcdir: "$target/bin", includes: "*.sh", eol: "unix")
    buildContext.ant.fixcrlf(srcdir: "$target/bin", includes: "*.py", eol: "unix")
  }

  private String buildMacZip(String macDistPath) {
    return buildContext.messages.block("Build zip archive for Mac OS") {
      def extraBins = customizer.extraExecutables
      def allPaths = [buildContext.paths.distAll, macDistPath]
      String zipRoot = "${customizer.getRootDirectoryName(buildContext.applicationInfo, buildContext.buildNumber)}/Contents"
      def targetPath = "$buildContext.paths.artifacts/${buildContext.productProperties.getBaseArtifactName(buildContext.applicationInfo, buildContext.buildNumber)}.mac.zip"
      buildContext.messages.progress("Building zip archive for Mac OS")
      buildContext.ant.zip(zipfile: targetPath) {
        allPaths.each {
          zipfileset(dir: it, prefix: zipRoot) {
            exclude(name: "bin/*.sh")
            exclude(name: "bin/*.py")
            exclude(name: "bin/fsnotifier")
            exclude(name: "bin/restarter")
            exclude(name: "MacOS/*")
            exclude(name: "build.txt")
            exclude(name: "NOTICE.txt")
            extraBins.each {
              exclude(name: it)
            }
            exclude(name: "bin/idea.properties")
          }
        }

        allPaths.each {
          zipfileset(dir: it, filemode: "755", prefix: zipRoot) {
            include(name: "bin/*.sh")
            include(name: "bin/*.py")
            include(name: "bin/fsnotifier")
            include(name: "bin/restarter")
            include(name: "MacOS/*")
            extraBins.each {
              include(name: it)
            }
          }
        }

        allPaths.each {
          zipfileset(dir: it, prefix: "$zipRoot/Resources") {
            include(name: "build.txt")
            include(name: "NOTICE.txt")
          }
        }

        zipfileset(file: "$macDistPath/bin/idea.properties", prefix: "$zipRoot/bin")
      }
      return targetPath
    }
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
  private Map<String, String> readIdeaProperties(File propertiesFile, Map<String, String> customProperties = [:]) {
    Map<String, String> ideaProperties = [:]
    propertiesFile.withReader {
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