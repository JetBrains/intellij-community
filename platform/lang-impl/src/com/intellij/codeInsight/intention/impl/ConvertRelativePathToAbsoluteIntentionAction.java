// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 */
public class ConvertRelativePathToAbsoluteIntentionAction extends ConvertAbsolutePathToRelativeIntentionAction {

  @Override
  protected boolean isConvertToRelative() {
    return false;
  }

  @Override
  public @NotNull String getFamilyName() {
    return CodeInsightBundle.message("intention.family.convert.relative.path.to.absolute");
  }

  @Override
  public @NotNull String getText() {
    return CodeInsightBundle.message("intention.text.convert.path.to.absolute");
  }
}
