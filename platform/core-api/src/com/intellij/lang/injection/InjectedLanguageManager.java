// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.injection;

import com.intellij.injected.editor.DocumentWindow;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.List;

/**
 * A set of API methods for working with injected PSI: code that resides inside other (host) PSI, e.g. string literals, XML text, etc.
 */
public abstract class InjectedLanguageManager {
  public static InjectedLanguageManager getInstance(@NotNull Project project) {
    return project.getService(InjectedLanguageManager.class);
  }

  public static final Key<Boolean> FRANKENSTEIN_INJECTION = Key.create("FRANKENSTEIN_INJECTION");

  public abstract PsiLanguageInjectionHost getInjectionHost(@NotNull FileViewProvider injectedProvider);

  @Nullable
  public abstract PsiLanguageInjectionHost getInjectionHost(@NotNull PsiElement injectedElement);

  @NotNull
  public abstract TextRange injectedToHost(@NotNull PsiElement injectedContext, @NotNull TextRange injectedTextRange);
  public abstract int injectedToHost(@NotNull PsiElement injectedContext, int injectedOffset);
  public abstract int injectedToHost(@NotNull PsiElement injectedContext, int injectedOffset, boolean minHostOffset);

  /**
   * @deprecated use {@link MultiHostInjector#MULTIHOST_INJECTOR_EP_NAME extension point} for production and
   * {@link #registerMultiHostInjector(MultiHostInjector, Disposable)} for tests
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public abstract void registerMultiHostInjector(@NotNull MultiHostInjector injector);

  @TestOnly
  public abstract void registerMultiHostInjector(@NotNull MultiHostInjector injector, @NotNull Disposable parentDisposable);

  @NotNull
  public abstract String getUnescapedText(@NotNull PsiElement injectedNode);

  @NotNull
  public abstract List<TextRange> intersectWithAllEditableFragments(@NotNull PsiFile injectedPsi, @NotNull TextRange rangeToEdit);

  public abstract boolean isInjectedFragment(@NotNull PsiFile injectedFile);

  /**
   * Finds PSI element in injected fragment (if any) at the given offset in the host file.<p/>
   * E.g. if you injected XML {@code "<xxx/>"} into Java string literal {@code "String s = "<xxx/>";"} and the caret is at {@code "xxx"} then
   * this method will return XmlToken(XML_TAG_START) with the text {@code "xxx"}.<br/>
   * Invocation of this method on uncommitted {@code hostFile} can lead to unexpected results, including throwing an exception!
   */
  @Nullable
  public abstract PsiElement findInjectedElementAt(@NotNull PsiFile hostFile, int hostDocumentOffset);

  @Nullable
  public abstract List<Pair<PsiElement, TextRange>> getInjectedPsiFiles(@NotNull PsiElement host);

  public abstract void dropFileCaches(@NotNull PsiFile file);

  public abstract PsiFile getTopLevelFile(@NotNull PsiElement element);

  @NotNull
  public abstract List<DocumentWindow> getCachedInjectedDocumentsInRange(@NotNull PsiFile hostPsiFile, @NotNull TextRange range);

  public abstract void enumerate(@NotNull PsiElement host, @NotNull PsiLanguageInjectionHost.InjectedPsiVisitor visitor);
  public abstract void enumerateEx(@NotNull PsiElement host, @NotNull PsiFile containingFile, boolean probeUp, @NotNull PsiLanguageInjectionHost.InjectedPsiVisitor visitor);

  /**
   * @return the ranges in this document window that correspond to prefix/suffix injected text fragments and thus can't be edited and are not visible in the editor.
   */
  @NotNull
  public abstract List<TextRange> getNonEditableFragments(@NotNull DocumentWindow window);

  /**
   * This method can be invoked on an uncommitted document, before performing commit and using other methods here
   * (which don't work for uncommitted document).
   */
  public abstract boolean mightHaveInjectedFragmentAtOffset(@NotNull Document hostDocument, int hostOffset);
  @NotNull
  public abstract DocumentWindow freezeWindow(@NotNull DocumentWindow document);
}
