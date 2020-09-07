// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.errorTreeView;

import com.intellij.util.Consumer;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class HotfixData {
  private final String myId;
  private final String myErrorText;
  private final @Nls String myFixComment;
  private final Consumer<? super HotfixGate> myFix;

  public HotfixData(@NotNull final String id,
                    @NotNull final String errorText,
                    @NotNull @Nls String fixComment,
                    final Consumer<? super HotfixGate> fix) {
    myErrorText = errorText;
    myFixComment = fixComment;
    myFix = fix;
    myId = id;
  }

  public String getId() {
    return myId;
  }

  public String getErrorText() {
    return myErrorText;
  }

  public Consumer<? super HotfixGate> getFix() {
    return myFix;
  }

  public @Nls String getFixComment() {
    return myFixComment;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    HotfixData that = (HotfixData)o;

    if (!myId.equals(that.myId)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myId.hashCode();
  }
}
