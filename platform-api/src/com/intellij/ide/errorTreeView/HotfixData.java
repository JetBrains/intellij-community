package com.intellij.ide.errorTreeView;

import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

public class HotfixData {
  private final String myId;
  private final String myErrorText;
  private final String myFixComment;
  private final Consumer<HotfixGate> myFix;

  public HotfixData(@NotNull final String id, @NotNull final String errorText, @NotNull String fixComment, final Consumer<HotfixGate> fix) {
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

  public Consumer<HotfixGate> getFix() {
    return myFix;
  }

  public String getFixComment() {
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
