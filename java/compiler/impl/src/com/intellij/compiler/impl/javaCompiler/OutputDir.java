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
package com.intellij.compiler.impl.javaCompiler;

import org.jetbrains.annotations.NotNull;

/**
* @author cdr
*/
class OutputDir {
  private final String myPath;
  private final int myKind;

  OutputDir(@NotNull String path, int kind) {
    myPath = path;
    myKind = kind;
  }

  @NotNull
  public String getPath() {
    return myPath;
  }

  public int getKind() {
    return myKind;
  }

  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof OutputDir)) {
      return false;
    }

    final OutputDir outputDir = (OutputDir)o;

    return myKind == outputDir.myKind && myPath.equals(outputDir.myPath);

  }

  public int hashCode() {
    int result = myPath.hashCode();
    result = 29 * result + myKind;
    return result;
  }
}
