/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import java.util.Comparator;

class UrlComparator implements Comparator<String> {
  @Override
  public int compare(String url1, String url2) {
    return url1.compareToIgnoreCase(url2);
    /*
    url1 = removeJarSeparator(url1);
    url2 = removeJarSeparator(url2);
    String name1 = url1.substring(url1.lastIndexOf('/') + 1);
    String name2 = url2.substring(url2.lastIndexOf('/') + 1);
    return name1.compareToIgnoreCase(name2);
    */
  }

  /*
  private String removeJarSeparator(String url) {
    return url.endsWith(JarFileSystem.JAR_SEPARATOR)? url.substring(0, url.length() - JarFileSystem.JAR_SEPARATOR.length()) : url;
  }
  */
}
