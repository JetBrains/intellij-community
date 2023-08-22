// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui;

import org.intellij.lang.annotations.MagicConstant;

public final class MessageConstants {
  public static final int OK = 0;
  public static final int YES = 0;
  public static final int NO = 1;
  public static final int CANCEL = 2;

  @MagicConstant(intValues = {YES, NO})
  public @interface YesNoResult { }

  @MagicConstant(intValues = {OK, CANCEL})
  public @interface OkCancelResult { }

  @MagicConstant(intValues = {YES, NO, CANCEL})
  public @interface YesNoCancelResult { }

  private MessageConstants() {
  }
}
