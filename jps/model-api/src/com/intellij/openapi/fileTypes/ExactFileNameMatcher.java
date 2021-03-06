// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NotNull;

public class ExactFileNameMatcher implements FileNameMatcher {
  private final String myFileName;
  private final boolean myIgnoreCase;

  public ExactFileNameMatcher(@NotNull String fileName) {
    myFileName = fileName;
    myIgnoreCase = false;
  }

  public ExactFileNameMatcher(@NotNull String fileName, final boolean ignoreCase) {
    myFileName = fileName;
    myIgnoreCase = ignoreCase;
  }

  @Override
  public boolean acceptsCharSequence(@NotNull CharSequence fileName) {
    return Comparing.equal(fileName, myFileName, !myIgnoreCase);
  }

  @Override
  public @NotNull String getPresentableString() {
    return myFileName;
  }

  public String getFileName() {
    return myFileName;
  }

  public boolean isIgnoreCase() {
    return myIgnoreCase;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final ExactFileNameMatcher that = (ExactFileNameMatcher)o;

    if (!myFileName.equals(that.myFileName)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myFileName.hashCode();
  }

  @Override
  public String toString() {
    return getPresentableString();
  }
}
