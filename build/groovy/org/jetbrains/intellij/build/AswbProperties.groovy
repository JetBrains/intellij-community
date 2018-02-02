/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.intellij.build

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.impl.PluginLayout

import static org.jetbrains.intellij.build.impl.PluginLayout.plugin

@CompileStatic
class AswbProperties extends AndroidStudioProperties {
  AswbProperties(String home) {
    super(home)
  }

  @Override
  @CompileDynamic
  void copyAdditionalFiles(BuildContext buildContext, String targetDirectory) {
    super.copyAdditionalFiles(buildContext, targetDirectory)

    def root = "$buildContext.paths.communityHome/../.."
    buildContext.ant.unzip(src: "$root/bazel-bin/external/blaze/java/com/google/devtools/intellij/blaze/plugin/aswb/aswb_blaze.zip",
                           dest: "$targetDirectory/plugins")
  }

  @Override
  String getBaseArtifactName(ApplicationInfoProperties applicationInfo, String buildNumber) {"aswb-$buildNumber" }
}
