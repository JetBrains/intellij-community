/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class WindowsJavaFinder extends JavaHomeFinder {

  @NotNull
  @Override
  protected List<String> findExistingJdks() {
    File javaHome = getJavaHome();
    if (javaHome == null) return Collections.emptyList();

    ArrayList<String> result = new ArrayList<>();
    File javasFolder = javaHome.getParentFile();
    scanFolder(javasFolder, result);
    File parentFile = javasFolder.getParentFile();
    File root = parentFile != null ? parentFile.getParentFile() : null;
    String name = parentFile != null ? parentFile.getName() : "";
    if (name.contains("Program Files") && root != null) {
      String x86Suffix = " (x86)";
      boolean x86 = name.endsWith(x86Suffix) && name.length() > x86Suffix.length();
      File anotherJavasFolder;
      if (x86) {
        anotherJavasFolder = new File(root, name.substring(0, name.length() - x86Suffix.length()));
      }
      else {
        anotherJavasFolder = new File(root, name + x86Suffix);
      }
      if (anotherJavasFolder.isDirectory()) {
        scanFolder(new File(anotherJavasFolder, javasFolder.getName()), result);
      }
    }
    result.sort((o1, o2) -> Comparing.compare(JavaSdkVersion.fromVersionString(o2), JavaSdkVersion.fromVersionString(o1)));
    return result;
  }
}
