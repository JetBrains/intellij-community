// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis.encoding;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

public class EncodingReference implements PsiReference, EmptyResolveMessageProvider {
  private final PsiElement myElement;

  private final String myCharsetName;
  private final TextRange myRangeInElement;

  public EncodingReference(PsiElement element, String charsetName, TextRange rangeInElement) {
    myElement = element;
    myCharsetName = charsetName;
    myRangeInElement = rangeInElement;
  }

  @Override
  public @NotNull PsiElement getElement() {
    return myElement;
  }

  @Override
  public @NotNull TextRange getRangeInElement() {
    return myRangeInElement;
  }

  @Override
  public @Nullable PsiElement resolve() {
    return CharsetToolkit.forName(myCharsetName) == null ? null : myElement;
    //if (ApplicationManager.getApplication().isUnitTestMode()) return myValue; // tests do not have full JDK
    //String fqn = charset.getClass().getName();
    //return myValue.getManager().findClass(fqn, GlobalSearchScope.allScope(myValue.getProject()));
  }

  @Override
  public @NotNull String getCanonicalText() {
    return myCharsetName;
  }

  @Override
  public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
    return null;
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    return null;
  }

  @Override
  public boolean isReferenceTo(@NotNull PsiElement element) {
    return false;
  }

  @Override
  public Object @NotNull [] getVariants() {
    Charset[] charsets = CharsetToolkit.getAvailableCharsets();
    List<LookupElement> suggestions = new ArrayList<>(charsets.length);
    for (Charset charset : charsets) {
      suggestions.add(LookupElementBuilder.create(charset.name()).withCaseSensitivity(false));
    }
    return suggestions.toArray(LookupElement.EMPTY_ARRAY);
  }

  @Override
  public boolean isSoft() {
    return false;
  }

  @Override
  public @NotNull String getUnresolvedMessagePattern() {
    //noinspection UnresolvedPropertyKey
    return CodeInsightBundle.message("unknown.encoding.0");
  }
}
