// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated superfluous, extend {@link AbstractBaseJavaLocalInspectionTool} / {@link CustomSuppressableInspectionTool} instead
 * (to be removed in IDEA 2019).
 */
@Deprecated
public abstract class BaseJavaLocalInspectionTool extends AbstractBaseJavaLocalInspectionTool implements CustomSuppressableInspectionTool {
  @NotNull
  @Override
  public SuppressIntentionAction[] getSuppressActions(PsiElement element) {
    String shortName = getShortName();
    HighlightDisplayKey key = HighlightDisplayKey.find(shortName);
    if (key == null) {
      throw new AssertionError("HighlightDisplayKey.find(" + shortName + ") is null. Inspection: " + getClass());
    }
    return SuppressManager.getInstance().createSuppressActions(key);
  }
}