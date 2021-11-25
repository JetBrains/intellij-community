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

  private static String makePathsVar(String variableName, @NotNull List<String> jarNames) {
    if (jarNames.isEmpty()) {
      return ""
    }

    String classPath = "$variableName=\"\$IDE_HOME/lib/${jarNames[0]}\"\n"
    if (jarNames.size() == 1) {
      return classPath
    }
    return classPath + String.join("", jarNames[1..-1].collect { "$variableName=\"\$$variableName:\$IDE_HOME/lib/${it}\"\n" })
  }

  static void generateScripts(@NotNull BuildContext context,
                              @NotNull List<String> extraJarNames,
                              @NotNull Path distBinDir,
                              @NotNull OsFamily osFamily) {
    String classPathVarName = "CLASSPATH"
    String classPath = makePathsVar(classPathVarName, context.bootClassPathJarNames + extraJarNames)
    if (context.productProperties.toolsJarRequired) {
      classPath += "$classPathVarName=\"\$$classPathVarName:\$JDK/lib/tools.jar\"\n"
    }

    String additionalJvmArgs = String.join(" ", context.additionalJvmArguments)
    String bootClassPath = makePathsVar("BOOT_CLASS_PATH", context.xBootClassPathJarNames)
    if (!bootClassPath.isEmpty()) {
      //noinspection SpellCheckingInspection
      additionalJvmArgs += " -Xbootclasspath/a:\$BOOT_CLASS_PATH"
    }

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
          copyScript(file, target, baseName, additionalJvmArgs, defaultXmxParameter, bootClassPath, classPath, scriptName, context)
        }
      }
      BuildTasksImpl.copyInspectScript(context, distBinDir)
    }
    else if (osFamily == OsFamily.MACOS) {
      copyScript(sourceScriptDir.resolve(REMOTE_DEV_SCRIPT_FILE_NAME), distBinDir.resolve(REMOTE_DEV_SCRIPT_FILE_NAME),
                 baseName, additionalJvmArgs, defaultXmxParameter, bootClassPath, classPath, baseName, context)
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
                                 String bootClassPath,
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
      "class_path", bootClassPath + classPath,
      "script_name", scriptName,
      ))
  }
}
