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

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
/**
 * @author nik
 */
class BuildUtils {
  static String replaceAll(String text, Map<String, String> replacements, String marker = "__") {
    replacements.each {
      text = StringUtil.replace(text, "$marker$it.key$marker", it.value)
    }
    return text
  }

  static void copyAndPatchFile(String sourcePath, String targetPath, Map<String, String> replacements, String marker = "__") {
    FileUtil.createParentDirs(new File(targetPath))
    new File(targetPath).text = replaceAll(new File(sourcePath).text, replacements, marker)
  }
}