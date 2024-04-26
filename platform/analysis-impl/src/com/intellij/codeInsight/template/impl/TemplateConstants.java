// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.impl;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;

@ApiStatus.Internal
final class TemplateConstants {

  public static final char SPACE_CHAR = ' ';
  public static final char TAB_CHAR = '\t';
  public static final char ENTER_CHAR = '\n';
  public static final char DEFAULT_CHAR = 'D';
  public static final char CUSTOM_CHAR = 'C';
  public static final char NONE_CHAR = 'N';

  static final @NonNls String CONTEXT = "context";
}
