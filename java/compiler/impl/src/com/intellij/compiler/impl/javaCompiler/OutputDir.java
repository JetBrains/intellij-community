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
