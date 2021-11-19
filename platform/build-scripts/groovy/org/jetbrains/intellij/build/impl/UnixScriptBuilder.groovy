// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.OsFamily

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

final class UnixScriptBuilder {
  private static String makePathsVar(String variableName, List<String> jarNames) {
    if (jarNames.isEmpty()) {
      return ""
    }
    String classPath = "$variableName=\"\$IDE_HOME/lib/${jarNames[0]}\"\n"
    if (jarNames.size() == 1) {
      return classPath
    }
    return classPath + String.join("", jarNames[1..-1].collect { "$variableName=\"\$$variableName:\$IDE_HOME/lib/${it}\"\n" })
  }

  static void generateScripts(@NotNull BuildContext buildContext,
                              @NotNull List<String> extraJarNames,
                              @NotNull Path distBinDir,
                              OsFamily osFamily) {
    String baseName = buildContext.productProperties.baseFileName
    String scriptName = "${baseName}.sh"
    String fullName = buildContext.applicationInfo.productName
    String vmOptionsFileName = baseName

    String bootClassPath = makePathsVar("BOOT_CLASS_PATH", buildContext.xBootClassPathJarNames)
    String classPathVarName = "CLASSPATH"
    String classPath = makePathsVar(classPathVarName, buildContext.bootClassPathJarNames + extraJarNames)
    if (buildContext.productProperties.toolsJarRequired) {
      classPath += "$classPathVarName=\"\$$classPathVarName:\$JDK/lib/tools.jar\"\n"
    }

    List<String> additionalJvmArgs = buildContext.additionalJvmArguments
    if (!bootClassPath.isEmpty()) {
      additionalJvmArgs += "-Xbootclasspath/a:\$BOOT_CLASS_PATH"
    }

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

    if (!Files.exists(vmOptionsPath)) {
      throw new IllegalStateException("File '$vmOptionsPath' should be already generated at this point")
    }

    String defaultXmxParameter = Files.readAllLines(vmOptionsPath).find { it.startsWith("-Xmx") }
    if (defaultXmxParameter == null) {
      throw new IllegalStateException("-Xmx was not found in '$vmOptionsPath'")
    }

    boolean isRemoteDevEnabled = buildContext.productProperties.productLayout.bundledPluginModules.contains("intellij.remoteDevServer")

    buildContext.ant.copy(todir: distBinDir.toString()) {
      switch (osFamily) {
        case OsFamily.LINUX:
          fileset(dir: "$buildContext.paths.communityHome/platform/build-scripts/resources/linux/scripts") {
            if (!isRemoteDevEnabled) {
              exclude(name: "remote-dev-server.sh")
            }
          }
          break
        case OsFamily.MACOS:
          if (isRemoteDevEnabled) {
            fileset(file: "$buildContext.paths.communityHome/platform/build-scripts/resources/linux/scripts/remote-dev-server.sh")
          }
          break
        default:
          throw new IllegalStateException("Unknown OsFamily")
      }
      filterset(begintoken: "__", endtoken: "__") {
        filter(token: "product_full", value: fullName)
        filter(token: "product_uc", value: buildContext.productProperties.getEnvironmentVariableBaseName(buildContext.applicationInfo))
        filter(token: "product_vendor", value: buildContext.applicationInfo.shortCompanyName)
        filter(token: "product_code", value: buildContext.applicationInfo.productCode)
        filter(token: "vm_options", value: vmOptionsFileName)
        filter(token: "system_selector", value: buildContext.systemSelector)
        filter(token: "ide_jvm_args", value: additionalJvmArgs.join(' '))
        filter(token: "ide_default_xmx", value: defaultXmxParameter.strip())
        filter(token: "class_path", value: bootClassPath + classPath)
        switch (osFamily) {
          case OsFamily.LINUX:
            filter(token: "script_name", value: scriptName)
            break
          case OsFamily.MACOS:
            filter(token: "script_name", value: baseName)
            break
          default:
            throw new IllegalStateException("Unknown OsFamily")
        }
      }
    }

    if (osFamily == OsFamily.LINUX) {
      Files.move(distBinDir.resolve("executable-template.sh"), distBinDir.resolve(scriptName), StandardCopyOption.REPLACE_EXISTING)
      BuildTasksImpl.copyInspectScript(buildContext, distBinDir)
    }
    buildContext.ant.fixcrlf(srcdir: distBinDir.toString(), includes: "*.sh", eol: "unix")
  }
}
