// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.text.StringUtilRt
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.OsFamily

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
    if (!context.xBootClassPathJarNames.isEmpty()) {
      additionalJvmArguments = new ArrayList<>(additionalJvmArguments)
      String bootCp = String.join(':', context.xBootClassPathJarNames.collect { "\$IDE_HOME/lib/${it}" })
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
      BuildTasksImpl.copyInspectScript(context, distBinDir)
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

    Files.writeString(targetFile, BuildUtils.replaceAll(
      StringUtilRt.convertLineSeparators(Files.readString(sourceFile)),
      "__",
      "product_full", fullName,
      "product_uc", context.productProperties.getEnvironmentVariableBaseName(context.applicationInfo),
      "product_vendor", context.applicationInfo.shortCompanyName,
      "product_code", context.applicationInfo.productCode,
      "vm_options", vmOptionsFileName,
      "system_selector", context.systemSelector,
      "ide_jvm_args", additionalJvmArgs,
      "ide_default_xmx", defaultXmxParameter.strip(),
      "class_path", classPath,
      "script_name", scriptName,
      ))
  }
}
