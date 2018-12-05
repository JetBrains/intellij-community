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
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

class WindowsJavaFinder extends JavaHomeFinder {

  @NotNull
  @Override
  protected List<String> findExistingJdks() {
    ArrayList<String> result = new ArrayList<>();
    Set<File> roots = findRootsToScan();
    for (File root : roots) {
       scanFolder(root, result);
    }
    result.sort((o1, o2) -> {
      String name1 = new File(o1).getName();
      String name2 = new File(o2).getName();
      return Comparing.compare(JavaSdkVersion.fromVersionString(name2), JavaSdkVersion.fromVersionString(name1));
    });
    return result;
  }

  private static Set<File> findRootsToScan() {
    TreeSet<File> roots = new TreeSet<>();
    File javaHome = getJavaHome();
    if (javaHome != null) {
      roots.add(javaHome);
    }
    File[] fsRoots = File.listRoots();
    for (File root : fsRoots) {
      if (root.exists()) {
        File candidate = new File(new File(root, "Program Files"), "Java");
        if (candidate.isDirectory()) roots.add(candidate);
        candidate =  new File(new File(root, "Program Files (x86)"), "Java");
        if (candidate.isDirectory()) roots.add(candidate);
      }
    }
    return roots;
  }
}
