// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.core;

import com.intellij.injected.editor.DocumentWindow;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class CoreInjectedLanguageManager extends InjectedLanguageManager {
  CoreInjectedLanguageManager() {}

  @Override
  public PsiLanguageInjectionHost getInjectionHost(@NotNull FileViewProvider injectedProvider) {
    return null;
  }

  @Nullable
  @Override
  public PsiLanguageInjectionHost getInjectionHost(@NotNull PsiElement injectedElement) {
    return null;
  }

  @NotNull
  @Override
  public TextRange injectedToHost(@NotNull PsiElement injectedContext, @NotNull TextRange injectedTextRange) {
    return injectedTextRange;
  }

  @Override
  public int injectedToHost(@NotNull PsiElement injectedContext, int injectedOffset) {
    return 0;
  }

  @Override
  public int injectedToHost(@NotNull PsiElement injectedContext, int injectedOffset, boolean minHostOffset) {
    return 0;
  }

  @Override
  public void registerMultiHostInjector(@NotNull MultiHostInjector injector, @NotNull Disposable parentDisposable) {

  }

  @NotNull
  @Override
  public String getUnescapedText(@NotNull PsiElement injectedNode) {
    return injectedNode.getText();
  }

  @NotNull
  @Override
  public List<TextRange> intersectWithAllEditableFragments(@NotNull PsiFile injectedPsi, @NotNull TextRange rangeToEdit) {
    return Collections.singletonList(rangeToEdit);
  }

  @Override
  public boolean isInjectedFragment(@NotNull PsiFile injectedFile) {
    return false;
  }

  @Nullable
  @Override
  public PsiElement findInjectedElementAt(@NotNull PsiFile hostFile, int hostDocumentOffset) {
    return null;
  }

  @Nullable
  @Override
  public List<Pair<PsiElement, TextRange>> getInjectedPsiFiles(@NotNull PsiElement host) {
    return null;
  }

  @Override
  public void dropFileCaches(@NotNull PsiFile file) {

  }

  @Override
  public PsiFile getTopLevelFile(@NotNull PsiElement element) {
    return element.getContainingFile();
  }

  @NotNull
  @Override
  public List<DocumentWindow> getCachedInjectedDocumentsInRange(@NotNull PsiFile hostPsiFile, @NotNull TextRange range) {
    return Collections.emptyList();
  }

  @Override
  public void enumerate(@NotNull PsiElement host, @NotNull PsiLanguageInjectionHost.InjectedPsiVisitor visitor) {

  }

  @Override
  public void enumerateEx(@NotNull PsiElement host,
                          @NotNull PsiFile containingFile,
                          boolean probeUp,
                          @NotNull PsiLanguageInjectionHost.InjectedPsiVisitor visitor) {

  }

  @NotNull
  @Override
  public List<TextRange> getNonEditableFragments(@NotNull DocumentWindow window) {
    return Collections.emptyList();
  }

  @Override
  public boolean mightHaveInjectedFragmentAtOffset(@NotNull Document hostDocument, int hostOffset) {
    return false;
  }

  @NotNull
  @Override
  public DocumentWindow freezeWindow(@NotNull DocumentWindow document) {
    return document;
  }
}
