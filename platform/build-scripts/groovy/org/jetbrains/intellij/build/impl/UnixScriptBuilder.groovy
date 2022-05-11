// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import groovy.transform.CompileStatic
import kotlin.Pair
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.io.FileKt

import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path

@CompileStatic
final class UnixScriptBuilder {
  private static final String REMOTE_DEV_SCRIPT_FILE_NAME = "remote-dev-server.sh"

  static void generateScripts(@NotNull BuildContext context,
                              @NotNull List<String> extraJarNames,
                              @NotNull Path distBinDir,
                              @NotNull OsFamily osFamily) {
    List<String> classPathJars = context.bootClassPathJarNames + extraJarNames
    String classPath = "CLASS_PATH=\"\$IDE_HOME/lib/${classPathJars.get(0)}\""
    for (int i = 1; i < classPathJars.size(); i++) {
      classPath += "\nCLASS_PATH=\"\$CLASS_PATH:\$IDE_HOME/lib/${classPathJars.get(i)}\""
    }

    List<String> additionalJvmArguments = context.additionalJvmArguments
    if (!context.getXBootClassPathJarNames().isEmpty()) {
      additionalJvmArguments = new ArrayList<>(additionalJvmArguments)
      String bootCp = String.join(':', context.getXBootClassPathJarNames().collect { "\$IDE_HOME/lib/${it}" })
      additionalJvmArguments.add('"-Xbootclasspath/a:' + bootCp + '"')
    }
    String additionalJvmArgs = String.join(' ', additionalJvmArguments)

    String baseName = context.productProperties.baseFileName

    Path vmOptionsPath
    switch (osFamily) {
      case OsFamily.LINUX:
        vmOptionsPath = distBinDir.resolve("${baseName}64.vmoptions")
        break
      case OsFamily.MACOS:
        vmOptionsPath = distBinDir.resolve("${baseName}.vmoptions")
        break
      default:
        throw new IllegalStateException("Unknown OsFamily")
    }

    String defaultXmxParameter
    try {
      defaultXmxParameter = Files.readAllLines(vmOptionsPath).find { it.startsWith("-Xmx") }
    }
    catch (NoSuchFileException e) {
      throw new IllegalStateException("File '$vmOptionsPath' should be already generated at this point", e)
    }

    if (defaultXmxParameter == null) {
      throw new IllegalStateException("-Xmx was not found in '$vmOptionsPath'")
    }

    boolean isRemoteDevEnabled = context.productProperties.productLayout.bundledPluginModules.contains("intellij.remoteDevServer")

    Files.createDirectories(distBinDir)
    Path sourceScriptDir = context.paths.communityHomeDir.resolve("platform/build-scripts/resources/linux/scripts")

    if (osFamily == OsFamily.LINUX) {
      String scriptName = baseName + ".sh"
      Files.newDirectoryStream(sourceScriptDir).withCloseable {
        for (Path file : it) {
          String fileName = file.fileName.toString()
          if (!isRemoteDevEnabled && fileName == REMOTE_DEV_SCRIPT_FILE_NAME) {
            continue
          }

          Path target = distBinDir.resolve(fileName == "executable-template.sh" ? scriptName : fileName)
          copyScript(file, target, baseName, additionalJvmArgs, defaultXmxParameter, classPath, scriptName, context)
        }
      }
      DistUtilKt.copyInspectScript(context, distBinDir)
    }
    else if (osFamily == OsFamily.MACOS) {
      copyScript(sourceScriptDir.resolve(REMOTE_DEV_SCRIPT_FILE_NAME), distBinDir.resolve(REMOTE_DEV_SCRIPT_FILE_NAME),
                 baseName, additionalJvmArgs, defaultXmxParameter, classPath, baseName, context)
    }
    else {
      throw new IllegalStateException("Unsupported OsFamily: $osFamily")
    }
  }

  private static void copyScript(Path sourceFile,
                                 Path targetFile,
                                 String vmOptionsFileName,
                                 String additionalJvmArgs,
                                 String defaultXmxParameter,
                                 String classPath,
                                 String scriptName,
                                 BuildContext context) {
    String fullName = context.applicationInfo.productName

    if (Files.readString(sourceFile).contains("\r")) {
      throw new IllegalStateException("File must not contain CR (\\r) separators: $sourceFile")
    }

    FileKt.substituteTemplatePlaceholders(
      sourceFile,
      targetFile,
      "__",
      [
        new Pair<String, String>("product_full", fullName),
        new Pair<String, String>("product_uc", context.productProperties.getEnvironmentVariableBaseName(context.applicationInfo)),
        new Pair<String, String>("product_vendor", context.applicationInfo.shortCompanyName),
        new Pair<String, String>("product_code", context.applicationInfo.productCode),
        new Pair<String, String>("vm_options", vmOptionsFileName),
        new Pair<String, String>("system_selector", context.systemSelector),
        new Pair<String, String>("ide_jvm_args", additionalJvmArgs),
        new Pair<String, String>("ide_default_xmx", defaultXmxParameter.strip()),
        new Pair<String, String>("class_path", classPath),
        new Pair<String, String>("script_name", scriptName),
      ],
      false
    )
  }
}
