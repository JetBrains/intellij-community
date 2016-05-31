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

import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.jps.model.module.JpsModuleSourceRoot
/**
 * @author nik
 */
class WindowsDistributionBuilder {
  private final BuildContext buildContext
  final String winDistPath

  WindowsDistributionBuilder(BuildContext buildContext) {
    this.buildContext = buildContext
    winDistPath = "$buildContext.paths.buildOutputRoot/dist.win"
  }

  //todo[nik] rename
  void layoutWin(File ideaProperties) {
    buildContext.ant.copy(todir: "$winDistPath/bin") {
      fileset(dir: "$buildContext.paths.communityHome/bin/win")
    }
    buildContext.ant.copy(file: ideaProperties.path, todir: "$winDistPath/bin")
    buildContext.ant.fixcrlf(file: "$winDistPath/bin/idea.properties", eol: "dos")

    buildContext.ant.copy(file: buildContext.productProperties.ico, tofile: "$winDistPath/bin/${buildContext.fileNamePrefix}.ico")
    if (buildContext.productProperties.windows.includeBatchLauncher) {
      winScripts()
    }
    winVMOptions()
    buildWinLauncher(JvmArchitecture.x32)
    buildWinLauncher(JvmArchitecture.x64)
    buildContext.productProperties.customWinLayout(winDistPath)
    buildWinZip()
  }

  //todo[nik] rename
  private void winScripts() {
    String fullName = buildContext.applicationInfo.productName
    String productUpperCase = buildContext.applicationInfo.shortProductName.toUpperCase()
    //todo[nik] looks like names without .exe were also supported, do we need this?
    String vmOptionsFileName = "${buildContext.fileNamePrefix}%BITS%.exe"

    String classPath = "SET CLASS_PATH=%IDE_HOME%\\lib\\${buildContext.bootClassPathJarNames[0]}\n"
    classPath += buildContext.bootClassPathJarNames[1..-1].collect { "SET CLASS_PATH=%CLASS_PATH%;%IDE_HOME%\\lib\\$it" }.join("\n")
    def jvmArgs = buildContext.productProperties.ideJvmArgs
    if (buildContext.productProperties.toolsJarRequired) {
      classPath += "\nSET CLASS_PATH=%CLASS_PATH%;%JDK%\\lib\\tools.jar"
      jvmArgs = "$jvmArgs -Didea.jre.check=true".trim()
    }

    def batName = "${buildContext.fileNamePrefix}.bat"
    buildContext.ant.copy(todir: "$winDistPath/bin") {
      fileset(dir: "$buildContext.paths.communityHome/bin/scripts/win")

      filterset(begintoken: "@@", endtoken: "@@") {
        filter(token: "product_full", value: fullName)
        filter(token: "product_uc", value: productUpperCase)
        filter(token: "vm_options", value: vmOptionsFileName)
        filter(token: "isEap", value: buildContext.applicationInfo.isEAP)
        filter(token: "system_selector", value: buildContext.systemSelector)
        filter(token: "ide_jvm_args", value: jvmArgs)
        filter(token: "class_path", value: classPath)
        filter(token: "script_name", value: batName)
      }
    }

    if (batName != "idea.bat") {
      //todo[nik] rename idea.bat in sources to something more generic
      buildContext.ant.move(file: "$winDistPath/bin/idea.bat", tofile: "$winDistPath/bin/$batName")
    }
    String inspectScript = buildContext.productProperties.customInspectScriptName
    if (inspectScript != null && inspectScript != "inspect") {
      buildContext.ant.move(file: "$winDistPath/bin/inspect.bat", tofile: "$winDistPath/bin/${inspectScript}.bat")
    }


    buildContext.ant.fixcrlf(srcdir: "$winDistPath/bin", includes: "*.bat", eol: "dos")
  }

  //todo[nik] rename
  private void winVMOptions() {
    JvmArchitecture.values().each {
      def yourkitSessionName = buildContext.applicationInfo.isEAP && buildContext.productProperties.includeYourkitAgentInEAP ? buildContext.systemSelector : null
      def fileName = "${buildContext.fileNamePrefix}${it.fileSuffix}.exe.vmoptions"
      new File(winDistPath, "bin/$fileName").text = VmOptionsGenerator.computeVmOptions(it, buildContext.applicationInfo.isEAP, yourkitSessionName).replace(' ', '\n')
    }

    buildContext.ant.fixcrlf(srcdir: "$winDistPath/bin", includes: "*.vmoptions", eol: "dos")
  }

  private void buildWinLauncher(JvmArchitecture arch) {
    buildContext.messages.block("Build Windows executable ${arch.name()}") {
      String exeFileName = "$buildContext.fileNamePrefix${arch.fileSuffix}.exe"
      def launcherPropertiesPath = "${buildContext.paths.temp}/launcher.properties"
      //todo[nik] generate launcher.properties file automatically
      def launcherPropertiesTemplatePath = arch == JvmArchitecture.x32 ? buildContext.productProperties.exe_launcher_properties
                                                                       : buildContext.productProperties.exe64_launcher_properties

      BuildUtils.copyAndPatchFile(launcherPropertiesTemplatePath, launcherPropertiesPath,
                                  ["PRODUCT_PATHS_SELECTOR": buildContext.systemSelector,
                                   "IDE-NAME": buildContext.applicationInfo.shortProductName.toUpperCase()])

      def communityHome = "$buildContext.paths.communityHome"
      String inputPath = "$communityHome/bin/WinLauncher/WinLauncher${arch.fileSuffix}.exe"
      buildContext.ant.java(classname: "com.pme.launcher.LauncherGeneratorMain", fork: "true", failonerror: "true") {
        sysproperty(key: "java.awt.headless", value: "true")
        arg(value: inputPath)
        arg(value: buildContext.findApplicationInfoInSources().absolutePath)
        arg(value: "$communityHome/native/WinLauncher/WinLauncher/resource.h")
        arg(value: launcherPropertiesPath)
        arg(value: "$winDistPath/bin/$exeFileName")
        classpath {
          pathelement(location: "$communityHome/build/lib/launcher-generator.jar")
          fileset(dir: "$communityHome/lib") {
            include(name: "guava*.jar")
            include(name: "jdom.jar")
            include(name: "sanselan*.jar")
          }
          [buildContext.findApplicationInfoModule(), buildContext.findModule("icons")].collectMany { it.sourceRoots }.each { JpsModuleSourceRoot root ->
            pathelement(location: root.file.absolutePath)
          }
        }
      }
    }
  }

  private void buildWinZip() {
    buildContext.messages.block("Build Windows .zip distribution") {
      def targetPath = "$buildContext.paths.artifacts/${buildContext.productProperties.archiveName(buildContext.buildNumber)}.win.zip"
      def zipPrefix = buildContext.productProperties.winAppRoot(buildContext.buildNumber)
      def dirs = [buildContext.paths.distAll, winDistPath]
      if (buildContext.productProperties.windows.bundleJre && new File(buildContext.paths.winJre).exists()) {
        dirs += buildContext.paths.winJre
      }
      buildContext.ant.zip(zipfile: targetPath) {
        dirs.each {
          zipfileset(dir: it, prefix: zipPrefix)
        }
      }
      buildContext.notifyArtifactBuilt(targetPath)
    }
  }
}