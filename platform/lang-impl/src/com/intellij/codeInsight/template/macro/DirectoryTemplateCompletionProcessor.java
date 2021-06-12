// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiFileSystemItem;


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
