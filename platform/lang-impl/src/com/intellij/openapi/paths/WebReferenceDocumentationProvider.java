// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.paths;

import com.intellij.ide.IdeBundle;
import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nls;

public class WebReferenceDocumentationProvider extends AbstractDocumentationProvider {
  @Override
  public @Nls String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
    if (element instanceof WebReference.MyFakePsiElement) {
      return IdeBundle.message("open.url.in.browser.tooltip");
    }
    return null;
  }
}
