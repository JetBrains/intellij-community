/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.psi.util;

import com.intellij.psi.PsiDirectory;
import org.jetbrains.annotations.NotNull;

public class ClassUtil {
  private ClassUtil() {}

  public static String extractPackageName(String className) {
    if (className != null) {
      int i = className.lastIndexOf('.');
      return i == -1 ? "" : className.substring(0, i);                                

    }
    return null;
  }

  public static String extractClassName(@NotNull String fqName) {
    int i = fqName.lastIndexOf('.');
    return i == -1 ? fqName : fqName.substring(i + 1);
  }

  public static String createNewClassQualifiedName(String qualifiedName, String className) {
    if (className == null){
      return null;
    }
    if (qualifiedName == null || qualifiedName.length() == 0){
      return className;
    }
    return qualifiedName + "." + extractClassName(className);
  }

  public static PsiDirectory sourceRoot(PsiDirectory containingDirectory) {
    while (containingDirectory != null) {
      if (containingDirectory.isSourceRoot()) {
        return containingDirectory;
      }
      containingDirectory = containingDirectory.getParentDirectory();
    }
    return null;
  }
}