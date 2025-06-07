// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.injection;

import com.intellij.lang.Language;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides ability to inject languages inside other PSI elements.
 * <p>
 * See {@link #startInjecting(Language)} for required workflow.
 *
 * @see MultiHostInjector
 */
public interface MultiHostRegistrar {

  /**
   * Start injecting the {@code language} in this place.
   * <p>
   * After calling {@code startInjecting()}, invoke
   * {@link #addPlace(String, String, PsiLanguageInjectionHost, TextRange)} one or several times
   * finished by {@link #doneInjecting()}.<br/>
   * Text in ranges denoted by one or several {@link #addPlace(String, String, PsiLanguageInjectionHost, TextRange)} calls
   * will be treated by the IDE as code in the {@code language}.
   * <p>
   * For example, in this Java fragment<br/>
   * {@code String x = "<start>" + "</start>"; }<br/>
   * calling
   * <ul>
   * <li>{@code startInjecting(XMLLanguage.getInstance());}</li>
   * <li>{@code addPlace(null, null, literal1, insideRange1);}</li>
   * <li>{@code addPlace(null, null, literal2, insideRange2);}</li>
   * <li>{@code doneInjecting();}</li>
   * </ul>
   * will inject XML language in both string literals, along with all its completion, navigation, etc. features.
   *
   * @return this
   */
  @NotNull
  MultiHostRegistrar startInjecting(@NotNull Language language);

  /**
   * The variant of {@link #startInjecting(Language)} with explicitly specified file extension.
   *
   * @param extension the created injected file name will have. Some parsers require specific extension. By default the extension is taken from the host file.
   */
  default @NotNull MultiHostRegistrar startInjecting(@NotNull Language language, @Nullable String extension) {
    return startInjecting(language);
  }

  /**
   * Specifies the range in the host file to be considered as injected file part.
   *
   * @param prefix          this part will be appended before.<br/>
   *                        For example. to treat the following Java string literal as a HTML inner text:<br/>
   *                        {@code String html = "Hello <b>world</b>";}<br/>
   *                        use {@code addPlace("<html><body>", "</body></html>", literal, insideRange); }
   * @param suffix          this part will be appended after.
   * @param host            the element in which the language will be injected into.
   * @param rangeInsideHost the text range inside the element into which the language will be injected.<br/>
   *                        For example, to inject something inside Java string literal {@code String s = "xyz";}<br/>
   *                        you will have to call {@code addPlace(prefix, suffix, literal, new TextRange(1, 4));} to inject inside double quotes.<br/>
   *                        Injected file document text will be of length = 3 and equals to 'xyz'.<br/>
   *                        If, however, you called {@code addPlace(prefix, suffix, literal, new TextRange(0, 5));} instead,<br/>
   *                        the injected file text would consist of five characters '"', 'x', 'y', 'z', '"'.<p/>
   * @return this
   * @see #startInjecting(Language) for required workflow.
   */
  @NotNull
  MultiHostRegistrar addPlace(@NonNls @Nullable String prefix,
                              @NonNls @Nullable String suffix,
                              @NotNull PsiLanguageInjectionHost host,
                              @NotNull TextRange rangeInsideHost);


  /**
   * Allows notifying the injected language support that it should use
   * less strict inspections. This is useful to turn off most of the
   * inspections in case the injection is guess based on the
   * contents of the string. It can also be useful when it is expected
   * that the injected code may be incorrect, for instance, when
   * the injection is just a code fragment in documentation.
   * <p>
   * The language support should use {@link InjectedLanguageManager#shouldInspectionsBeLenient(PsiElement)}
   * to check for the value passed to this method.
   *
   * @param shouldInspectionsBeLenient specify whether inspections within the injection
   *                                   should be lenient. By default, this is {@code false}.
   * @return this
   *
   * @see InjectedLanguageManager#shouldInspectionsBeLenient(PsiElement)
   * @see MultiHostRegistrar#frankensteinInjection(boolean)
   */
  @NotNull
  default MultiHostRegistrar makeInspectionsLenient(boolean shouldInspectionsBeLenient) {
    return putInjectedFileUserData(InjectedLanguageManager.LENIENT_INSPECTIONS, shouldInspectionsBeLenient ? true : null);
  }

  /**
   * Allows notifying the injected language support that it should not
   * check for errors at all, because the contents of the injection could
   * not be evaluated completely at the compile time.
   * <p>
   * The language support should use {@link InjectedLanguageManager#isFrankensteinInjection(PsiElement)}
   * to check for the value passed to this method.
   *
   * @param isFrankensteinInjection specify whether the contents of the injection
   *                                are fully known at the compile time
   * @return this
   *
   * @see InjectedLanguageManager#isFrankensteinInjection(PsiElement)
   * @see MultiHostRegistrar#makeInspectionsLenient(boolean)
   */
  @NotNull
  default MultiHostRegistrar frankensteinInjection(boolean isFrankensteinInjection) {
    return putInjectedFileUserData(InjectedLanguageManager.FRANKENSTEIN_INJECTION, isFrankensteinInjection ? true : null);
  }

  /**
   * Allows setting custom user data on the injected file.
   *
   * @return this
   */
  @NotNull
  default <T> MultiHostRegistrar putInjectedFileUserData(Key<T> key, T data) {
    throw new UnsupportedOperationException();
  }

  /**
   * The final part of the injecting process.
   * Must be invoke to notify IDE constructing the injection has finished.
   *
   * @see #startInjecting(Language) for required workflow.
   */
  void doneInjecting();
}