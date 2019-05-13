/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.lang.injection;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import com.intellij.lang.Language;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.openapi.util.TextRange;

/**
 * Provides ability to inject languages inside other PSI elements.<br/>
 * E.g. inject SQL inside XML tag text or inject RegExp into Java string literals.<br/>
 * These injected fragments are treated by IDE as separate tiny files in a specific language and corresponding code insight features,<br/>
 * like completion, highlighting, navigation become available there.<br/>
 * You can get hold of an instance of {@link MultiHostRegistrar} by registering your own implementation of {@link MultiHostInjector} and<br/>
 * implementing its {@link MultiHostInjector#getLanguagesToInject(MultiHostRegistrar, com.intellij.psi.PsiElement)} method.<br/>
 */
public interface MultiHostRegistrar {
  /**
   * Start injecting the {@code language} in this place.<p/>
   * After you call {@link #startInjecting(Language)} you will have to call
   * {@link #addPlace(String, String, PsiLanguageInjectionHost, TextRange)} one or several times
   * and then call {@link #doneInjecting()}.<br/>
   * After that the text in ranges (denoted by one or several {@link #addPlace(String, String, PsiLanguageInjectionHost, TextRange)} calls)
   * will be treated by IDE as a code in the {@code language}.<br/>
   * For example, in this Java fragment<br/>
   * {@code String x = "<start>"+"</start>"; }<br/>
   * if you call<br/>
   * - {@code startInjecting(XMLLanguage.getInstance());}<br/>
   * - {@code addPlace(null, null, literal1, insideRange1);}<br/>
   * - {@code addPlace(null, null, literal2, insideRange2);}<br/>
   * - {@code doneInjecting();}<br/>
   * You will have XML language injected in these string literals, along with its completion, navigation etc niceties.
   * @return this
   */
  @NotNull /*this*/
  MultiHostRegistrar startInjecting(@NotNull Language language);

  /**
   * The variant of {@link #startInjecting(Language)} with explicitly specified file extension.
   * @param extension the created injected file name will have. Some parsers require specific extension. By default the extension is taken from the host file.
   */
  @NotNull
  default /*this*/
  MultiHostRegistrar startInjecting(@NotNull Language language, @Nullable String extension) {
    return startInjecting(language);
  }

  /**
   * Specifies the range in the host file to be considered as injected file part.
   * @see #startInjecting(Language) for the required workflow.
   * @param prefix this part will be appended before.<br/>
   *               For example. to treat the following Java string literal as a HTML inner text:<br/>
   *               {@code String html = "Hello <b>world</b>";}<br/>
   *               You can call {@code addPlace("<html><body>", "</body></html>", literal, insideRange); }<p/>
   *
   * @param suffix this part will be appended after.<p/>
   *
   * @param host the text range of which the language will be injected into.<p/>
   *
   * @param rangeInsideHost into which the language will be injected.<br/>
   *                        For example, to inject something inside Java string literal {@code String s = "xyz";}<br/>
   *                        you will have to call {@code addPlace(prefix, suffix, literal, new TextRange(1, 4));} to inject inside double quotes.<br/>
   *                        Injected file document text will be of length = 3 and equals to 'xyz'.<br/>
   *                        If, however, you called {@code addPlace(prefix, suffix, literal, new TextRange(0, 5));} instead,<br/>
   *                        the injected file text would consist of five characters '"', 'x', 'y', 'z', '"'.<p/>
   */
  @NotNull /*this*/
  MultiHostRegistrar addPlace(@NonNls @Nullable String prefix, @NonNls @Nullable String suffix, @NotNull PsiLanguageInjectionHost host, @NotNull TextRange rangeInsideHost);


  /**
   * The final part of the injecting process.
   * You have to call this method to tell the IDE you finished constructing the injection.
   * @see #startInjecting(Language) for a required workflow.
   */
  void doneInjecting();

}