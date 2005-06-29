package com.intellij.newCodeFormatting;

public interface Wrap {
  public int ALWAYS = 0;
  public int NORMAL = 1;
  public int NONE = 2;
  public int CHOP_DOWN_IF_LONG = 3;

  void ignoreParentWraps();
}
