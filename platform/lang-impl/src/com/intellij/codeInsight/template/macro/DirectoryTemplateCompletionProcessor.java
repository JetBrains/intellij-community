// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.psi.PsiFileSystemItem;
import org.jetbrains.annotations.ApiStatus;


@ApiStatus.Internal
public class DirectoryTemplateCompletionProcessor implements TemplateCompletionProcessor {
  @Override
  public boolean nextTabOnItemSelected(final ExpressionContext context, final LookupElement item) {
    Object object = item.getObject();
    if (object instanceof PsiFileSystemItem && ((PsiFileSystemItem)object).isDirectory()) {
      return false;
    }
    return true;
  }
}
