// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.injection;

import com.intellij.lang.Language;
import com.intellij.openapi.util.TextRange;
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
  @NotNull
  default MultiHostRegistrar startInjecting(@NotNull Language language, @Nullable String extension) {
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
   * The final part of the injecting process.
   * Must be invoke to notify IDE constructing the injection has finished.
   *
   * @see #startInjecting(Language) for required workflow.
   */
  void doneInjecting();
}