// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle;


import com.intellij.openapi.editor.Document;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;


@ApiStatus.Internal
public final class TimeStampedIndentOptions extends CommonCodeStyleSettings.IndentOptions {
  private long myTimeStamp;
  private int myOriginalIndentOptionsHash;
  private boolean myDetected;

  TimeStampedIndentOptions(CommonCodeStyleSettings.IndentOptions toCopyFrom, long timeStamp) {
    copyFrom(toCopyFrom);
    myTimeStamp = timeStamp;
    myOriginalIndentOptionsHash = toCopyFrom.hashCode();
  }

  public void setDetected(boolean isDetected) {
    myDetected = isDetected;
  }

  void setTimeStamp(long timeStamp) {
    myTimeStamp = timeStamp;
  }

  public void setOriginalIndentOptionsHash(int originalIndentOptionsHash) {
    myOriginalIndentOptionsHash = originalIndentOptionsHash;
  }

  public boolean isOutdated(@NotNull Document document, @NotNull CommonCodeStyleSettings.IndentOptions defaultForFile) {
    return document.getModificationStamp() != myTimeStamp
           || defaultForFile.hashCode() != myOriginalIndentOptionsHash;
  }

  public boolean isDetected() {
    return myDetected;
  }
}
