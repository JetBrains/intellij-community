/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInsight.hints;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

public interface InlayParameterHintsProvider {

  /**
   * Hints for params to be shown, hints offsets should be located within element's text range.
   */
  @NotNull
  List<InlayInfo> getParameterHints(PsiElement element);

  /**
   * Provides hint info, for alt-enter action (can be {@link HintInfo.MethodInfo} or {@link HintInfo.OptionInfo}).
   * <p>
   * MethodInfo: provides fully qualified method name (e.g. "java.util.Map.put") and list of its parameter names.
   * Used to match method with blacklist, and to add method into blacklist
   * <p>
   * OptionInfo: provides option to disable/enable by alt-enter
   */
  @Nullable
  HintInfo getHintInfo(PsiElement element);

  /**
   * Default list of patterns for which hints should not be shown.
   */
  @NotNull
  Set<String> getDefaultBlackList();

  /**
   * Returns language which blacklist will be appended to the resulting one.
   * E.g. to prevent possible Groovy and Kotlin extensions from showing hints for blacklisted Java methods.
   */
  @Nullable
  default Language getBlackListDependencyLanguage() {
    return null;
  }

  /**
   * List of supported options, shown in settings dialog.
   */
  @NotNull
  default List<Option> getSupportedOptions() {
    return ContainerUtil.emptyList();
  }

  /**
   * If {@code false} no blacklist panel will be shown in "Parameter Name Hints Settings".
   */
  default boolean isBlackListSupported() {
    return true;
  }

  /**
   * Text explaining black list patterns.
   */
  default String getBlacklistExplanationHTML() {
    return CodeInsightBundle.message("inlay.hints.blacklist.pattern.explanation");
  }

  /**
   * Customise hints presentation.
   */
  default String getInlayPresentation(@NotNull String inlayText) {
    return inlayText + ":";
  }

  /**
   * Whether provider should be queried for hints ({@link #getParameterHints(PsiElement)}) even if showing hints is disabled globally
   * ({@link com.intellij.openapi.editor.ex.EditorSettingsExternalizable#isShowParameterNameHints()}).
   */
  default boolean canShowHintsWhenDisabled() { return false; }

  /**
   * @return text of preview, will be used in settings.
   */
  default String getSettingsPreview() { return null; }

  /**
   * @return {@code true} if set of options is complete and if all options are off, provider will collect no hints.
   */
  default boolean isExhaustive() {
    return false;
  }

  /**
   * @return text of main checkbox in hints settings
   */
  default String getMainCheckboxText() {
    return "Show parameter hints";
  }
}
