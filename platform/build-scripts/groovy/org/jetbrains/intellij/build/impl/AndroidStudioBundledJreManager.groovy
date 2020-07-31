/*
 * Copyright (C) 2019 The Android Open Source Project
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
package org.jetbrains.intellij.build.impl

import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.OsFamily

class AndroidStudioBundledJreManager extends BundledJreManager {
  BuildContext buildContext
  String baseDirectoryForJdk

  AndroidStudioBundledJreManager(BuildContext buildContext, String communityHome) {
    super(buildContext)
    this.buildContext = buildContext
    this.baseDirectoryForJdk = "$communityHome/../../prebuilts/studio/jdk"
  }

  String extractJre(String osName) {
    return "$baseDirectoryForJdk/$osName"
  }

  @Override
  String extractJre(OsFamily os) {
    return extractJre(os.jbrArchiveSuffix)
  }
}
