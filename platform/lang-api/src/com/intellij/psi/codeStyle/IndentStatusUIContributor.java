// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings.IndentOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class IndentStatusUIContributor implements CodeStyleStatusUIContributor {
  private final IndentOptions myIndentOptions;

  public IndentStatusUIContributor(IndentOptions options) {
    myIndentOptions = options;
  }

  public IndentOptions getIndentOptions() {
    return myIndentOptions;
  }

  /**
   * Checks if any actions are available for the given virtual file and indent options without creating them. Indent options may not
   * necessarily be from the same {@code FileIndentOptionsProvider} but still the provider may offer its own actions in such case.
   *
   * @param file          The current virtual file.
   * @return True if any actions are available, false otherwise.
   */
  @Override
  public boolean areActionsAvailable(@NotNull VirtualFile file) {
    return false;
  }

  /**
   * @param file          The current virtual file
   * @return An array of actions available for the given virtual file and indent options or {@code null} if no actions are available.
   */
  @Override
  @Nullable
  public AnAction[] getActions(@NotNull PsiFile file) {
    return null;
  }

  /**
   * Returns a tooltip string to inform a user about the given indent options. The default implementation returns the following tooltip:
   * "x spaces/Tab (hint)", where "hint" is an optional short string returned by {@link #getHint()}
   * @return The tooltip string or {@code null} if the tooltip is not available.
   */
  @Override
  @NotNull
  public String getTooltip() {
    return getTooltip(getIndentOptions(), getHint());
  }

  /**
   * Returns a short, usually one-word, string to indicate the source of the given indent options.
   *
   * @return The indent options source hint or {@code null} if not available.
   */
  @Override
  @Nullable
  public String getHint() {
    return null;
  }


  @NotNull
  public static String getTooltip(@NotNull IndentOptions indentOptions, @Nullable String hint) {
    StringBuilder sb = new StringBuilder();
    if (indentOptions.USE_TAB_CHARACTER) {
      sb.append("Tab");
    }
    else {
      int indent = indentOptions.INDENT_SIZE;
      sb.append(indentOptions.INDENT_SIZE).append(indent > 1 ? " spaces" : " space");
    }
    if (hint != null) sb.append(" (").append(hint).append(')');
    return sb.toString();
  }


  /**
   * Returns a text shown in a popup to drag user's attention to a UI element associated with the current indent options and related actions.
   * The advertisement text may contain basic information about the source of the current indent options so that a user becomes aware of it.
   * The popup is supposed to be shown just once per a case which requires explanation. Subsequent calls to the method may return {@code null}.
   *
   * @param psiFile       A PSI file to show the advertisement text for.
   * @return The text to be shown or null for no popup.
   */
  @Override
  @Nullable
  public String getAdvertisementText(@NotNull PsiFile psiFile) {
    return null;
  }

  /**
   * @return True if "Configure indents for [Language]" action should be available when the provider is active (returns its own indent
   *          options), false otherwise.
   */
  public boolean isShowFileIndentOptionsEnabled() {
    return true;
  }


  /**
   * Creates an action which can be used to disable the provider.
   *
   * @param project The project to disable the provider in.
   * @return The disable action.
   */
  @Override
  @Nullable
  public AnAction createDisableAction(@NotNull Project project) {
    return null;
  }
}

