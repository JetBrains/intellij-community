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
/**
 * @author nik
 */
class CrossPlatformDistributionBuilder {
  private final BuildContext buildContext

  CrossPlatformDistributionBuilder(BuildContext buildContext) {
    this.buildContext = buildContext
  }

  void buildCrossPlatformZip(String winDistPath, String linuxDistPath, String macDistPath) {
    buildContext.messages.block("Building cross-platform zip") {
      def executableName = buildContext.productProperties.baseFileName
      def zipDir = "$buildContext.paths.temp/cross-platform-zip"
      buildContext.ant.copy(todir: "$zipDir/bin/win") {
        fileset(dir: "$winDistPath/bin") {
          include(name: "idea.properties")
        }
      }
      buildContext.ant.copy(todir: "$zipDir/bin/linux") {
        fileset(dir: "$linuxDistPath/bin") {
          include(name: "*.vmoptions")
          include(name: "idea.properties")
        }
      }
      buildContext.ant.copy(todir: "$zipDir/bin/mac") {
        fileset(dir: "$macDistPath/bin") {
          include(name: "${executableName}.vmoptions")
          include(name: "idea.properties")
        }
      }
      buildContext.ant.copy(file: "$macDistPath/bin/${executableName}.vmoptions", tofile: "$zipDir/bin/mac/${executableName}64.vmoptions")
      buildContext.ant.copy(todir: "$zipDir/bin") {
        fileset(dir: "$macDistPath/bin") {
          include(name: "*.jnilib")
        }
        mapper(type: "glob", from: "*.jnilib", to: "*.dylib")
      }

      String targetPath = "$buildContext.paths.artifacts/${buildContext.productProperties.getBaseArtifactName(buildContext.applicationInfo, buildContext.buildNumber)}.zip"
      buildContext.ant.zip(zipfile: targetPath, duplicate: "fail") {
        fileset(dir: buildContext.paths.distAll) {
          exclude(name: "bin/idea.properties")
        }
        fileset(dir: zipDir)

        fileset(dir: winDistPath) {
          exclude(name: "bin/fsnotifier*.exe")
          exclude(name: "bin/*.exe.vmoptions")
          exclude(name: "bin/${executableName}*.exe")
          exclude(name: "bin/idea.properties")
        }
        zipfileset(dir: "$winDistPath/bin", prefix: "bin/win") {
          include(name: "fsnotifier*.exe")
          include(name: "*.exe.vmoptions")
        }

        fileset(dir: linuxDistPath) {
          exclude(name: "bin/fsnotifier*")
          exclude(name: "bin/*.vmoptions")
          exclude(name: "bin/*.sh")
          exclude(name: "bin/*.py")
          exclude(name: "bin/idea.properties")
          exclude(name: "help/**")
        }
        zipfileset(dir: "$linuxDistPath/bin", prefix: "bin", filemode: "775") {
          include(name: "*.sh")
          include(name: "*.py")
        }
        zipfileset(dir: "$linuxDistPath/bin", prefix: "bin/linux", filemode: "775") {
          include(name: "fsnotifier*")
        }

        fileset(dir: macDistPath) {
          exclude(name: "bin/fsnotifier*")
          exclude(name: "bin/restarter*")
          exclude(name: "bin/*.sh")
          exclude(name: "bin/*.py")
          exclude(name: "bin/*.jnilib")
          exclude(name: "bin/idea.properties")
          exclude(name: "bin/*.vmoptions")
        }
        zipfileset(dir: "$macDistPath/bin", prefix: "bin", filemode: "775") {
          include(name: "restarter*")
        }
        zipfileset(dir: "$macDistPath/bin", prefix: "bin/mac", filemode: "775") {
          include(name: "fsnotifier*")
        }
      }
      buildContext.notifyArtifactBuilt(targetPath)
    }
  }
}