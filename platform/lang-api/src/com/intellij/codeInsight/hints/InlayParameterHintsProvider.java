// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public interface InlayParameterHintsProvider {

  /**
   * Hints for params to be shown, hints offsets should be located within element's text range.
   */
  @NotNull
  default List<InlayInfo> getParameterHints(@NotNull PsiElement element) {
    return Collections.emptyList();
  }

  @NotNull
  default List<InlayInfo> getParameterHints(@NotNull PsiElement element, @NotNull PsiFile file) {
    return getParameterHints(element);
  }

  /**
   * Provides hint info, for alt-enter action (can be {@link HintInfo.MethodInfo} or {@link HintInfo.OptionInfo}).
   * <p>
   * MethodInfo: provides fully qualified method name (e.g. "java.util.Map.put") and list of its parameter names.
   * Used to match method with blacklist, and to add method into exclude list
   * <p>
   * OptionInfo: provides option to disable/enable by alt-enter
   */
  @Nullable
  default HintInfo getHintInfo(@NotNull PsiElement element) {
    return null;
  }

  @Nullable
  default HintInfo getHintInfo(@NotNull PsiElement element, @NotNull PsiFile file) {
    return getHintInfo(element);
  }

  /**
   * Default list of patterns for which hints should not be shown.
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
   * If {@code false} no blacklist panel will be shown in "Parameter Name Hints Settings".
   */
  default boolean isBlackListSupported() {
    return true;
  }

  /**
   * Text explaining exclude list patterns.
   */
  default String getBlacklistExplanationHTML() {
    return CodeInsightBundle.message("inlay.hints.blacklist.pattern.explanation");
  }

  /**
   * Customise hints presentation.
   */
  @NotNull
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
    return CodeInsightBundle.message("settings.inlay.show.parameter.hints");
  }

  /**
   * @return Traverser for `root` element subtree.
   */
  @NotNull
  default SyntaxTraverser<PsiElement> createTraversal(@NotNull PsiElement root) {
    return SyntaxTraverser.psiTraverser(root);
  }
}
