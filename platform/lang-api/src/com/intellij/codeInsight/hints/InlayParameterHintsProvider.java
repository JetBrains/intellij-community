// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Provides simple text inlays (info elements rendered inside source code) for a given language.
 * The order of hints which share the same offset is not guaranteed.
 *
 * @see InlayHintsProvider for more interactive inlays.
 */
public interface InlayParameterHintsProvider {

  /**
   * @param element element for which hints should be shown.
   * @return list of hints to be shown, hints offsets should be located within element's text range.
   */
  @NotNull
  default List<InlayInfo> getParameterHints(@NotNull PsiElement element) {
    return Collections.emptyList();
  }

  /**
   * @param file file which holds element.
   * @param element element for which hints should be shown.
   * @return list of hints to be shown, hints offsets should be located within element's text range.
   */
  @NotNull
  default List<InlayInfo> getParameterHints(@NotNull PsiElement element, @NotNull PsiFile file) {
    return getParameterHints(element);
  }

  /**
   * Provides information about hint for intention actions (can be {@link HintInfo.MethodInfo} or {@link HintInfo.OptionInfo})
   * which allow enabling/disabling hints at a given position.
   *
   * Make sure that this method executed fast enough to run on EDT.
   *
   * @param element the element under the caret
   *
   */
  @Nullable
  default HintInfo getHintInfo(@NotNull PsiElement element) {
    return null;
  }

  /**
   * Provides information about hint for intention actions (can be {@link HintInfo.MethodInfo} or {@link HintInfo.OptionInfo})
   * which allow enabling/disabling hints at a given position.
   *
   * Make sure that this method executed fast enough to run on EDT.
   *
   * @param element the element under the caret
   */
  @Nullable
  default HintInfo getHintInfo(@NotNull PsiElement element, @NotNull PsiFile file) {
    return getHintInfo(element);
  }

  /**
   * Exclude list - default list of patterns for which hints should not be shown.
   */
  @NotNull
  Set<String> getDefaultBlackList();

  /**
   * Returns language which exclude list will be appended to the resulting one.
   * E.g. to prevent possible Groovy and Kotlin extensions from showing hints for excluded Java methods.
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
   * If {@code false} no exclude list panel will be shown in "File | Settings | Editor | Inlay Hints | Language | Parameter Hints".
   */
  default boolean isBlackListSupported() {
    return true;
  }

  /**
   * Text explaining exclude list patterns.
   */
  @Nls
  default String getBlacklistExplanationHTML() {
    return CodeInsightBundle.message("inlay.hints.exclude.list.pattern.explanation");
  }

  /**
   * Customise hints presentation.
   */
  @NotNull
  default String getInlayPresentation(@NotNull String inlayText) {
    return inlayText + ":";
  }

  /**
   * Whether provider should be queried for hints ({@link #getParameterHints(PsiElement)}) even if showing hints is disabled globally.
   * ({@link com.intellij.openapi.editor.ex.EditorSettingsExternalizable#isShowParameterNameHints()}).
   */
  default boolean canShowHintsWhenDisabled() { return false; }

  /**
   * @deprecated the text is not rendered in settings anymore, do not implement this method.
   */
  @Deprecated
  default String getSettingsPreview() { return null; }

  /**
   * @return {@code true} if set of options is exhaustive and if all options are disabled, provider will collect no hints.
   */
  default boolean isExhaustive() {
    return false;
  }

  /**
   * @return text of main checkbox in hints settings.
   */
  default String getMainCheckboxText() {
    return CodeInsightBundle.message("settings.inlay.show.parameter.hints");
  }

  /**
   * @return Traverser for `root` element subtree.
   */
  @NotNull
  default SyntaxTraverser<PsiElement> createTraversal(@NotNull PsiElement root) {
    return SyntaxTraverser.psiTraverser(root);
  }

  @Nls
  default String getDescription() { return null; }

  /**
   * @param key bundle key of the option.
   * @return description of the given option or null (in this case it won't be shown).
   */
  @Nls
  @Nullable
  default String getProperty(String key) { return null; }
}
