// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.model.psi.PsiExternalReferenceHost;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Marks PSI element as (potentially) containing text in other language.
 * Injected language PSI does not embed into the PSI tree of the hosting element,
 * but is used by the IDE for highlighting, completion and other code insight actions.
 * <p>
 * In order to do the injection, you have to
 * <ul>
 * <li>Implement {@link com.intellij.psi.LanguageInjector} to describe exact place where injection should occur.</li>
 * <li>Register injection in {@link com.intellij.psi.LanguageInjector#EXTENSION_POINT_NAME} extension point.</li>
 * </ul>
 * Currently, language can be injected into string literals, XML tag contents and XML attributes.
 * <p>
 * You don't have to implement {@code PsiLanguageInjectionHost} by yourself, unless you want to inject something into your own custom PSI.
 * For all returned injected PSI elements, {@link InjectedLanguageManager#getInjectionHost(PsiElement)} returns {@code PsiLanguageInjectionHost} they were injected into.
 */
public interface PsiLanguageInjectionHost extends PsiExternalReferenceHost {

  /**
   * @return {@code true} if this instance can accept injections, {@code false} otherwise
   */
  boolean isValidHost();

  /**
   * Update the host element using the provided text of the injected file. It may be required to escape characters from {@code text}
   * in accordance with the host language syntax. The implementation may delegate to {@link ElementManipulators#handleContentChange(PsiElement, String)}
   * if {@link ElementManipulator} implementation is registered for this element class.
   *
   * @param text text of the injected file
   * @return the updated instance
   */
  PsiLanguageInjectionHost updateText(@NotNull String text);

  /**
   * @return {@link LiteralTextEscaper} instance which will be used to convert the content of this host element to the content of injected file
   */
  @NotNull
  LiteralTextEscaper<? extends PsiLanguageInjectionHost> createLiteralTextEscaper();


  @FunctionalInterface
  interface InjectedPsiVisitor {
    void visit(@NotNull PsiFile injectedPsi, @NotNull List<Shred> places);
  }

  interface Shred {
    @Nullable("returns null when the host document marker is invalid")
    Segment getHostRangeMarker();

    @NotNull
    TextRange getRangeInsideHost();

    boolean isValid();

    void dispose();

    @Nullable
    PsiLanguageInjectionHost getHost();

    /**
     * @return range in decoded PSI
     */
    @NotNull
    TextRange getRange();

    @NotNull
    String getPrefix();

    @NotNull
    String getSuffix();
  }
}
