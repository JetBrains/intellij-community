// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.paths;

import com.intellij.ide.BrowserUtil;
import com.intellij.model.psi.PsiExternalReferenceHost;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.FakePsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class WebReference extends PsiReferenceBase<PsiElement> {
  private final @Nullable String myUrl;
  
  public WebReference(@NotNull PsiElement element) {
    this(element, (String)null);
  }
  
  public WebReference(@NotNull PsiElement element, @Nullable String url) {
    super(element, true);
    myUrl = url;
  }

  public WebReference(@NotNull PsiElement element, @NotNull TextRange textRange) {
    this(element, textRange, null);
  }

  public WebReference(@NotNull PsiElement element, TextRange textRange, @Nullable String url) {
    super(element, textRange, true);
    myUrl = url;
  }

  public boolean isHttpRequestTarget() {
    return true;
  }

  @Override
  public PsiElement resolve() {
    return new MyFakePsiElement();
  }

  public String getUrl() {
    return myUrl != null ? myUrl : getValue();
  }

  final class MyFakePsiElement extends FakePsiElement implements SyntheticElement {
    @Override
    public PsiElement getParent() {
      return myElement;
    }

    @Override
    public void navigate(boolean requestFocus) {
      BrowserUtil.browse(getUrl());
    }

    @Override
    public String getPresentableText() {
      return getUrl();
    }


    @Override
    public String getName() {
      return getUrl();
    }

    @Override
    public TextRange getTextRange() {
      final TextRange rangeInElement = getRangeInElement();
      final TextRange elementRange = myElement.getTextRange();
      return elementRange != null ? rangeInElement.shiftRight(elementRange.getStartOffset()) : rangeInElement;
    }
  }

  /**
   * Optimization method to greatly reduce frequency of potentially expensive {@link PsiElement#getReferences()} calls
   * @return true if the element is able to contain WebReference
   */
  public static boolean isWebReferenceWorthy(@NotNull PsiElement element) {
    return element instanceof HintedReferenceHost || element instanceof ContributedReferenceHost || element instanceof PsiExternalReferenceHost;
  }
}
