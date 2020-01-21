// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.fileTypes;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ExtensionFileNameMatcher implements FileNameMatcher {
  private final String myExtension;
  private final String myDotExtension;

  public ExtensionFileNameMatcher(@NotNull @NonNls String extension) {
    myExtension = StringUtil.toLowerCase(extension);
    myDotExtension = "." + myExtension;
  }

  @Override
  public boolean acceptsCharSequence(@NonNls @NotNull CharSequence fileName) {
    return StringUtilRt.endsWithIgnoreCase(fileName, myDotExtension);
  }

  @Override
  @NonNls
  @NotNull
  public String getPresentableString() {
    return "*." + myExtension;
  }

  @NotNull
  public String getExtension() {
    return myExtension;
  }


  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final ExtensionFileNameMatcher that = (ExtensionFileNameMatcher)o;

    return myExtension.equals(that.myExtension);
  }

  @Override
  public int hashCode() {
    return myExtension.hashCode();
  }

  @Override
  public String toString() {
    return getPresentableString();
  }
}
