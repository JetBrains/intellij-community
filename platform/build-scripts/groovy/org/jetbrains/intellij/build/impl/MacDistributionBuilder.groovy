// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.util.SystemProperties
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import io.opentelemetry.api.trace.Span
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.impl.productInfo.ProductInfoGenerator
import org.jetbrains.intellij.build.impl.productInfo.ProductInfoValidator

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.util.concurrent.ForkJoinTask
import java.util.function.BiConsumer
import java.util.function.Supplier
import java.util.zip.Deflater

import static org.jetbrains.intellij.build.impl.TracerManager.spanBuilder

@CompileStatic
final class MacDistributionBuilder extends OsSpecificDistributionBuilder {
  private final MacDistributionCustomizer customizer
  private final Path ideaProperties
  @SuppressWarnings('SpellCheckingInspection')
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
    List<String> associations = new ArrayList<>()

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

    return String.join("\n      ", associations) + customizer.additionalDocTypes
  }

  @Override
  void copyFilesForOsDistribution(@NotNull Path macDistDir, JvmArchitecture arch = null) {
    doCopyExtraFiles(macDistDir, arch, true)
  }

  private void doCopyExtraFiles(Path macDistDir, JvmArchitecture arch, boolean copyDistFiles) {
    //noinspection SpellCheckingInspection
    List<String> platformProperties = new ArrayList<String>(Arrays.asList(
      "\n#---------------------------------------------------------------------",
      "# macOS-specific system properties",
      "#---------------------------------------------------------------------",
      "com.apple.mrj.application.live-resize=false",
      "apple.laf.useScreenMenuBar=true",
      "jbScreenMenuBar.enabled=true",
      "apple.awt.fileDialogForDirectories=true",
      "apple.awt.graphics.UseQuartz=true",
      "apple.awt.fullscreencapturealldisplays=false"
    ))
    customizer.getCustomIdeaProperties(buildContext.applicationInfo).forEach(new BiConsumer<String, String>() {
      @Override
      void accept(String k, String v) {
        platformProperties.add(k + '=' + v)
      }
    })

    layoutMacApp(ideaProperties, platformProperties, getDocTypes(), macDistDir, buildContext)

    BuildTasksImpl.unpackPty4jNative(buildContext, macDistDir, "darwin")

    BuildTasksImpl.generateBuildTxt(buildContext, macDistDir.resolve("Resources"))
    if (copyDistFiles) {
      BuildTasksImpl.copyDistFiles(buildContext, macDistDir)
    }

    customizer.copyAdditionalFiles(buildContext, macDistDir.toString())
    if (arch != null) {
      customizer.copyAdditionalFiles(buildContext, macDistDir, arch)
    }

    UnixScriptBuilder.generateScripts(buildContext, Collections.<String>emptyList(), macDistDir.resolve("bin"), OsFamily.MACOS)
  }

  @Override
  void buildArtifacts(@NotNull Path osSpecificDistDir) {
    doCopyExtraFiles(osSpecificDistDir, null, false)
    buildContext.executeStep("build macOS artifacts", BuildOptions.MAC_ARTIFACTS_STEP, new Runnable() {
      @Override
      void run() {
        doBuildArtifacts(osSpecificDistDir, customizer, buildContext)
      }
    })
  }

  private static void doBuildArtifacts(Path osSpecificDistDir, MacDistributionCustomizer customizer, BuildContext context) {
    String baseName = context.productProperties.getBaseArtifactName(context.applicationInfo, context.buildNumber)
    boolean publishArchive = context.proprietaryBuildTools.macHostProperties == null

    Path macZip = ((publishArchive || customizer.publishArchive) ? context.paths.artifactDir : context.paths.tempDir)
      .resolve(baseName + ".mac.zip")
    String zipRoot = getZipRoot(context, customizer)

    BuildHelper buildHelper = BuildHelper.getInstance(context)
    buildHelper.buildMacZip.invokeWithArguments(macZip,
                                                zipRoot,
                                                generateProductJson(context, null),
                                                context.paths.distAllDir,
                                                osSpecificDistDir,
                                                context.getDistFiles(),
                                                getExecutableFilePatterns(customizer),
                                                publishArchive ? Deflater.DEFAULT_COMPRESSION : Deflater.BEST_SPEED)
    ProductInfoValidator.checkInArchive(context, macZip, "$zipRoot/Resources")

    if (publishArchive) {
      Span.current().addEvent("skip DMG artifact producing because a macOS build agent isn't configured")
      context.notifyArtifactBuilt(macZip)
      return
    }

    boolean notarize = SystemProperties.getBooleanProperty("intellij.build.mac.notarize", true)
    BuildHelper.invokeAllSettled(List.of(
      createBuildForArchTask(JvmArchitecture.x64, macZip, notarize, customizer, context),
      createBuildForArchTask(JvmArchitecture.aarch64, macZip, notarize, customizer, context),
    ))
    Files.deleteIfExists(macZip)
  }

  private static ForkJoinTask<?> createBuildForArchTask(JvmArchitecture arch,
                                                        Path macZip,
                                                        Boolean notarize,
                                                        MacDistributionCustomizer customizer,
                                                        BuildContext context) {
    return BuildHelper.getInstance(context).createTask(spanBuilder("build macOS artifacts for specific arch")
                                                         .setAttribute("arch", arch.name()), new Supplier<Void>() {
      @Override
      Void get() {
        ForkJoinTask.invokeAll(buildForArch(arch, context.bundledRuntime, macZip, notarize, customizer, context)
                                 .findAll { it != null })

        return null
      }
    })
  }

  private static List<ForkJoinTask<?>> buildForArch(JvmArchitecture arch,
                                                    BundledRuntime jreManager,
                                                    Path macZip,
                                                    boolean notarize,
                                                    MacDistributionCustomizer customizer,
                                                    BuildContext context) {
    List<ForkJoinTask<?>> tasks = new ArrayList<ForkJoinTask<?>>()
    String suffix = arch == JvmArchitecture.x64 ? "" : "-${arch.fileSuffix}"
    String archStr = arch.name()
    // with JRE
    if (context.options.buildDmgWithBundledJre) {
      Path additional = context.paths.tempDir.resolve("mac-additional-files-for-" + archStr)
      Files.createDirectories(additional)
      customizer.copyAdditionalFiles(context, additional, arch)
      List<String> binariesToSign = customizer.getBinariesToSign(context, arch)
      if (!binariesToSign.isEmpty()) {
        context.executeStep(spanBuilder("sign binaries for macOS distribution")
                              .setAttribute("arch", arch.name()), BuildOptions.MAC_SIGN_STEP, new Runnable() {
          @Override
          void run() {
            context.signFiles(binariesToSign.collect { additional.resolve(it) }, Map.of(
              "mac_codesign_options", "runtime",
              "mac_codesign_force", "true",
              "mac_codesign_deep", "true",
              ))
          }
        })
      }

      tasks.add(BuildHelper.getInstance(context).createSkippableTask(
        spanBuilder("build DMG with JRE").setAttribute("arch", archStr),
        "${BuildOptions.MAC_ARTIFACTS_STEP}_jre_$archStr",
        context,
        new Runnable() {
          @Override
          void run() {
            Path jreArchive = jreManager.findArchive(BundledRuntime.getProductPrefix(context), OsFamily.MACOS, arch)
            MacDmgBuilder.signAndBuildDmg(context, customizer, context.proprietaryBuildTools.macHostProperties, macZip,
                                          additional, jreArchive, suffix, notarize)
          }
        }))
    }

    // without JRE
    if (context.options.buildDmgWithoutBundledJre) {
      tasks.add(BuildHelper.getInstance(context).createSkippableTask(
        spanBuilder("build DMG without JRE").setAttribute("arch", archStr),
        "${BuildOptions.MAC_ARTIFACTS_STEP}_no_jre_$archStr",
        context, new Runnable() {
        @Override
        void run() {
          MacDmgBuilder.signAndBuildDmg(context, customizer, context.proprietaryBuildTools.macHostProperties, macZip,
                                        null, null, "-no-jdk$suffix", notarize)
        }
      }))
    }
    return tasks
  }

  private void layoutMacApp(Path ideaPropertiesFile,
                            List<String> platformProperties,
                            String docTypes,
                            Path macDistDir,
                            BuildContext context) {
    MacDistributionCustomizer macCustomizer = customizer
    BuildHelper buildHelper = BuildHelper.getInstance(context)
    buildHelper.copyDirWithFileFilter(context.paths.communityHomeDir.resolve("bin/mac"),
                                      macDistDir.resolve("bin"),
                                      customizer.binFilesFilter)
    buildHelper.copyDir(context.paths.communityHomeDir.resolve("platform/build-scripts/resources/mac/Contents"), macDistDir)

    String executable = context.productProperties.baseFileName
    Files.move(macDistDir.resolve("MacOS/executable"), macDistDir.resolve("MacOS/$executable"))

    //noinspection SpellCheckingInspection
    Path icnsPath = Path.of((context.applicationInfo.isEAP ? customizer.icnsPathForEAP : null) ?: customizer.icnsPath)
    Path resourcesDistDir = macDistDir.resolve("Resources")
    BuildHelper.copyFile(icnsPath, resourcesDistDir.resolve(targetIcnsFileName))

    for (FileAssociation fileAssociation in customizer.fileAssociations) {
      if (!fileAssociation.iconPath.empty) {
        Path source = Path.of(fileAssociation.iconPath)
        Path dest = resourcesDistDir.resolve(source.fileName)
        Files.deleteIfExists(dest)
        BuildHelper.copyFile(source, dest)
      }
    }

    String fullName = context.applicationInfo.productName

    //todo[nik] improve
    String minor = context.applicationInfo.minorVersion
    boolean isNotRelease = context.applicationInfo.isEAP && !minor.contains("RC") && !minor.contains("Beta")
    String version = isNotRelease ? "EAP $context.fullBuildNumber" : "${context.applicationInfo.majorVersion}.${minor}"
    String EAP = isNotRelease ? "-EAP" : ""

    List<String> properties = Files.readAllLines(ideaPropertiesFile)
    properties.addAll(platformProperties)
    Files.write(macDistDir.resolve("bin/idea.properties"), properties)

    String bootClassPath = String.join(":", context.xBootClassPathJarNames.collect { "\$APP_PACKAGE/Contents/lib/$it" })
    String classPath = String.join(":", context.bootClassPathJarNames.collect { "\$APP_PACKAGE/Contents/lib/$it" })

    List<String> fileVmOptions = VmOptionsGenerator.computeVmOptions(context.applicationInfo.isEAP, context.productProperties)
    List<String> additionalJvmArgs = context.additionalJvmArguments
    if (!bootClassPath.isEmpty()) {
      additionalJvmArgs = new ArrayList<>(additionalJvmArgs)
      //noinspection SpellCheckingInspection
      additionalJvmArgs.add("-Xbootclasspath/a:" + bootClassPath)
    }
    List<List<String>> propsAndOpts = additionalJvmArgs.split { it.startsWith('-D') }
    List<String> launcherProperties = propsAndOpts[0], launcherVmOptions = propsAndOpts[1]

    fileVmOptions.add("-XX:ErrorFile=\$USER_HOME/java_error_in_${executable}_%p.log".toString())
    fileVmOptions.add("-XX:HeapDumpPath=\$USER_HOME/java_error_in_${executable}.hprof".toString())
    Files.writeString(macDistDir.resolve("bin/${executable}.vmoptions"), String.join('\n', fileVmOptions) + '\n', StandardCharsets.US_ASCII)

    String vmOptionsXml = optionsToXml(launcherVmOptions)
    String vmPropertiesXml = propertiesToXml(launcherProperties, ['idea.executable': context.productProperties.baseFileName])

    String archString = '<key>LSArchitecturePriority</key>\n    <array>\n'
    for (String it in macCustomizer.architectures) {
      archString += '      <string>' + it + '</string>\n'
    }
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
    String todayYear = LocalDate.now().year.toString()
    //noinspection SpellCheckingInspection
    BuildUtils.replaceAll(macDistDir.resolve("Info.plist"), "@@",
      "build", context.fullBuildNumber,
      "doc_types", docTypes ?: "",
      "executable", executable,
      "icns", targetIcnsFileName,
      "bundle_name", fullName,
      "product_state", EAP,
      "bundle_identifier", macCustomizer.bundleIdentifier,
      "year", todayYear,
      "company_name", context.applicationInfo.companyName,
      "min_year", "2000",
      "max_year", todayYear,
      "version", version,
      "vm_options", vmOptionsXml,
      "vm_properties", vmPropertiesXml,
      "class_path", classPath,
      "url_schemes", urlSchemesString,
      "architectures", archString,
      "min_osx", macCustomizer.minOSXVersion,
    )

    Path distBinDir = macDistDir.resolve("bin")
    Files.createDirectories(distBinDir)

    Path sourceScriptDir = context.paths.communityHomeDir.resolve("platform/build-scripts/resources/mac/scripts")
    Files.newDirectoryStream(sourceScriptDir).withCloseable {stream ->
      String inspectCommandName = context.productProperties.inspectCommandName
      for (Path file : stream) {
        if (file.toString().endsWith(".sh")) {
          String content = BuildUtils.replaceAll(
            Files.readString(file), "@@",
            "product_full", fullName,
            "script_name", executable,
            "inspectCommandName", inspectCommandName,
          )

          String fileName = file.fileName.toString()
          if (fileName == "inspect.sh" && inspectCommandName != "inspect") {
            fileName = "${inspectCommandName}.sh"
          }

          Files.writeString(distBinDir.resolve(fileName), StringUtilRt.convertLineSeparators(content))
        }
      }
    }
  }

  @Override
  List<String> generateExecutableFilesPatterns(boolean includeJre) {
    return getExecutableFilePatterns(customizer)
  }

  private static List<String> getExecutableFilePatterns(MacDistributionCustomizer customizer) {
    //noinspection SpellCheckingInspection
    return [
             "bin/*.sh",
             "bin/*.py",
             "bin/fsnotifier",
             "bin/printenv",
             "bin/restarter",
             "MacOS/*"
           ] + customizer.extraExecutables
  }

  static String getZipRoot(BuildContext buildContext, MacDistributionCustomizer customizer) {
    return "${customizer.getRootDirectoryName(buildContext.applicationInfo, buildContext.buildNumber)}/Contents"
  }

  static byte[] generateProductJson(BuildContext buildContext, String javaExecutablePath) {
    String executable = buildContext.productProperties.baseFileName
    return new ProductInfoGenerator(buildContext).generateProductJson("../bin", null,
                                                                      "../MacOS/${executable}", javaExecutablePath,
                                                                      "../bin/${executable}.vmoptions", OsFamily.MACOS)
  }

  private static String optionsToXml(List<String> options) {
    StringBuilder buff = new StringBuilder()
    for (String it in options) {
      buff.append('        <string>').append(it).append('</string>\n')
    }
    return buff.toString().trim()
  }

  private static String propertiesToXml(List<String> properties, Map<String, String> moreProperties) {
    StringBuilder buff = new StringBuilder()
    for (String it in properties) {
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
