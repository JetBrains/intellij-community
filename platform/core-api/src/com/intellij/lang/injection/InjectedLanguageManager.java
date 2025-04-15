// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.injection;

import com.intellij.injected.editor.DocumentWindow;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import org.jetbrains.annotations.*;

import java.util.List;

/**
 * A set of API methods for working with injected PSI: code that resides inside other (host) PSI, e.g. string literals, XML text, etc.
 */
public abstract class InjectedLanguageManager {
  public static InjectedLanguageManager getInstance(@NotNull Project project) {
    return project.getService(InjectedLanguageManager.class);
  }

  /**
   * Used to indicate a psi file should not be checked for errors, see e.g. {@link org.intellij.plugins.intelliLang.inject.FrankensteinErrorFilter}.
   * Set in user data ({@link UserDataHolder}) on for example a string expression that can not be completely evaluated at compile time.
   */
  public static final Key<Boolean> FRANKENSTEIN_INJECTION = Key.create("FRANKENSTEIN_INJECTION");

  public abstract PsiLanguageInjectionHost getInjectionHost(@NotNull FileViewProvider injectedProvider);

  public abstract @Nullable PsiLanguageInjectionHost getInjectionHost(@NotNull PsiElement injectedElement);

  /**
   * @return range in the top level file if {@code injectedContext} is inside injection
   *         unchanged {@code injectedTextRange} otherwise
   */
  public abstract @NotNull TextRange injectedToHost(@NotNull PsiElement injectedContext, @NotNull TextRange injectedTextRange);
  public abstract int injectedToHost(@NotNull PsiElement injectedContext, int injectedOffset);
  public abstract int injectedToHost(@NotNull PsiElement injectedContext, int injectedOffset, boolean minHostOffset);

  @TestOnly
  public abstract void registerMultiHostInjector(@NotNull MultiHostInjector injector, @NotNull Disposable parentDisposable);

  public abstract @NotNull String getUnescapedText(@NotNull PsiElement injectedNode);

  /**
   * @param injectedFile injected file
   * @param injectedOffset offset inside the injected file (e.g., caret position from the editor)
   * @return the corresponding unescaped offset which matches the result of {@link #getUnescapedText(PsiElement)}
   */
  @Contract(pure = true)
  public int mapInjectedOffsetToUnescaped(@NotNull PsiFile injectedFile, int injectedOffset) {
    throw new UnsupportedOperationException();
  }

  @Contract(pure = true)
  public int mapUnescapedOffsetToInjected(@NotNull PsiFile injectedFile, int offset) {
    throw new UnsupportedOperationException();
  }

  public abstract @NotNull @Unmodifiable List<TextRange> intersectWithAllEditableFragments(@NotNull PsiFile injectedPsi, @NotNull TextRange rangeToEdit);

  public boolean isInjectedFragment(@NotNull PsiFile injectedFile) {
    return isInjectedViewProvider(injectedFile.getViewProvider());
  }

  public abstract boolean isInjectedViewProvider(@NotNull FileViewProvider viewProvider);

  /**
   * Finds PSI element in injected fragment (if any) at the given offset in the host file.<p/>
   * E.g. if you injected XML {@code "<xxx/>"} into Java string literal {@code "String s = "<xxx/>";"} and the caret is at {@code "xxx"} then
   * this method will return XmlToken(XML_TAG_START) with the text {@code "xxx"}.<br/>
   * Invocation of this method on uncommitted {@code hostFile} can lead to unexpected results, including throwing an exception!
   */
  public abstract @Nullable PsiElement findInjectedElementAt(@NotNull PsiFile hostFile, int hostDocumentOffset);

  public abstract @Nullable @Unmodifiable List<Pair<PsiElement, TextRange>> getInjectedPsiFiles(@NotNull PsiElement host);

  public boolean hasInjections(@NotNull PsiElement host) {
    return getInjectedPsiFiles(host) != null;
  }

  public abstract void dropFileCaches(@NotNull PsiFile file);

  public abstract PsiFile getTopLevelFile(@NotNull PsiElement element);

  public abstract @Unmodifiable @NotNull List<DocumentWindow> getCachedInjectedDocumentsInRange(@NotNull PsiFile hostPsiFile, @NotNull TextRange range);

  public abstract void enumerate(@NotNull PsiElement host, @NotNull PsiLanguageInjectionHost.InjectedPsiVisitor visitor);
  public abstract void enumerateEx(@NotNull PsiElement host, @NotNull PsiFile containingFile, boolean probeUp, @NotNull PsiLanguageInjectionHost.InjectedPsiVisitor visitor);

  /**
   * @return the ranges in this document window that correspond to prefix/suffix injected text fragments and thus can't be edited and are not visible in the editor.
   */
  public abstract @NotNull @Unmodifiable List<TextRange> getNonEditableFragments(@NotNull DocumentWindow window);

  /**
   * This method can be invoked on an uncommitted document, before performing commit and using other methods here
   * (which don't work for uncommitted document).
   */
  public abstract boolean mightHaveInjectedFragmentAtOffset(@NotNull Document hostDocument, int hostOffset);
  public abstract @NotNull DocumentWindow freezeWindow(@NotNull DocumentWindow document);
}
