// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
   * @deprecated use {@link InjectedLanguageManager#isFrankensteinInjection(PsiElement)} or {@link MultiHostRegistrar#frankensteinInjection(boolean)} instead.
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static final Key<Boolean> FRANKENSTEIN_INJECTION = Key.create("FRANKENSTEIN_INJECTION");

  @ApiStatus.Internal
  public static final Key<Boolean> LENIENT_INSPECTIONS = Key.create("LENIENT_INSPECTIONS");

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

  /**
   * The language should adjust severity and enablement of inspections based on the value provided to
   * {@link MultiHostRegistrar#makeInspectionsLenient(boolean)} when registering the injection.
   * Some injections should be treated as partial code fragments, where semantic errors are expected.
   * For instance, an injection within a Markdown file will most likely be a partial example, so it is
   * expected that semantic analysis should not be performed on it.
   * <p>
   * Note that this is different from {@link InjectedLanguageManager#isFrankensteinInjection}, since the injection
   * contents are expected to be fully evaluated at compile time.
   *
   * @param injectedContext a {@code PsiElement}, which might be from an injected file
   * @return if {@code injectedContext} is injected, value provided to {@link MultiHostRegistrar#makeInspectionsLenient(boolean)} if any,
   *         otherwise {@code false}
   *
   * @see InjectedLanguageManager#isFrankensteinInjection(PsiElement)
   */
  public abstract boolean shouldInspectionsBeLenient(@NotNull PsiElement injectedContext);

  /**
   * Used to indicate a psi file should not be checked for errors at all, because the contents of the injections could not be
   * evaluated completely at compile time.
   *
   * @param injectedContext a {@code PsiElement}, which might be from an injected file
   * @return {@code true} if {@code injectedContext} is injected and the contents of injection could not be evaluated completely
   *
   * @see InjectedLanguageManager#shouldInspectionsBeLenient(PsiElement)
   * @see org.intellij.plugins.intelliLang.inject.FrankensteinErrorFilter
   */
  public abstract boolean isFrankensteinInjection(@NotNull PsiElement injectedContext);
}
