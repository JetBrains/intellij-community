package com.intellij.psi;

import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Pair;

import java.util.List;

/**
 * Marks psi element as (potentially) containing text in other language.
 * Injected language PSI does not embed into the PSI tree of the hosting element,
 * but is used by IDEA for highlighting, completion and other code insight actions.
 * In order to do the injection, you have to
 * <ul>
 * <li>Implement {@link com.intellij.psi.LanguageInjector} to describe exact place where injection should occur.</li>  
 * <li>Register injection in {@link com.intellij.psi.PsiManager#registerLanguageInjector(LanguageInjector)} .</li>
 * </ul>
 * Currently, language can be injected into string literals, XML tag contents and XML attributes.
 * You don't have to implement PsiLanguageInjectionHost by yourself, unless you want to inject something into your own custom PSI.
 * For all returned injected PSI elements, {@link PsiElement#getContext()} method returns PsiLanguageInjectionHost they were injected into.
 */
public interface PsiLanguageInjectionHost extends PsiElement {
  /**
   * @return injected PSI element and text range inside host element where injection occurs.
   * For example, in string literals we might want to inject something inside double quotes.
   * To express this, use <code>return Pair.create(injectedPsi, new TextRange(1, textLength+1))</code>.
   */
  @Nullable
  List<Pair<PsiElement,TextRange>> getInjectedPsi();

  void fixText(String text);
}
