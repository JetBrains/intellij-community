/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.codeStyle.CommonCodeStyleSettings.IndentOptions;

/**
 * Provides indent options for a PSI file thus allowing different PSI files having different indentation policies withing the same project.
 * The provider can also offer ad hoc actions to control the current indentation policy without opening settings.
 */
public abstract class FileIndentOptionsProvider {

  public final static ExtensionPointName<FileIndentOptionsProvider> EP_NAME = ExtensionPointName.create("com.intellij.fileIndentOptionsProvider");

  /**
   * Retrieves indent options for PSI file.
   * @param settings Code style settings for which indent options are calculated.
   * @param file The file to retrieve options for.
   * @return Indent options or {@code null} if the provider can't retrieve them.
   */
  @Nullable
  public abstract IndentOptions getIndentOptions(@NotNull CodeStyleSettings settings, @NotNull PsiFile file);

  /**
   * Tells if the provider can be used when a complete file is reformatted.
   * @return True by default
   */
  public boolean useOnFullReformat() {
    return true;
  }

  /**
   * Checks if any actions are available for the given virtual file and indent options without creating them. Indent options may not
   * necessarily be from the same {@code FileIndentOptionsProvider} but still the provider may offer its own actions in such case.
   *
   * @param file          The current virtual file.
   * @param indentOptions The indent options to check actions' availability for.
   * @return True if any actions are available, false otherwise.
   */
  public boolean areActionsAvailable(@NotNull VirtualFile file, @NotNull IndentOptions indentOptions) {
    return false;
  }

  /**
   * @param file          The current virtual file
   * @param indentOptions The current indent options.
   * @return An array of actions available for the given virtual file and indent options or {@code null} if no actions are available.
   */
  @Nullable
  public AnAction[] getActions(@NotNull PsiFile file, @NotNull IndentOptions indentOptions) {
    return null;
  }

  /**
   * Returns a tooltip string to inform a user about the given indent options. The default implementation returns the following tooltip:
   * "x spaces/Tab (hint)", where "hint" is an optional short string returned by {@link #getHint(IndentOptions)}
   * @param indentOptions The indent options to return the tooltip for.
   * @return The tooltip string or {@code null} if the tooltip is not available.
   */
  @NotNull
  public String getTooltip(@NotNull IndentOptions indentOptions) {
    return getTooltip(indentOptions, getHint(indentOptions));
  }

  /**
   * Returns a short, usually one-word, string to indicate the source of the given indent options.
   *
   * @param indentOptions The indent options to return the hint for.
   * @return The indent options source hint or {@code null} if not available.
   */
  @Nullable
  public String getHint(@NotNull IndentOptions indentOptions) {
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
   * @param indentOptions The current indent options for the file.
   * @return The text to be shown or null for no popup.
   */
  @Nullable
  public String getAdvertisementText(@NotNull PsiFile psiFile, @NotNull IndentOptions indentOptions) {
    return null;
  }

  protected static void notifyIndentOptionsChanged(@NotNull Project project, @Nullable PsiFile file) {
    CodeStyleSettingsManager.getInstance(project).fireCodeStyleSettingsChanged(file);
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
  @Nullable
  public AnAction createDisableAction(@NotNull Project project) {
    return null;
  }

  /**
   * Creates a default action when the provider is inactive (disabled). Default actions are collected from multiple providers if none
   * of them is returning any indent options. The action may suggest to enable the provider if it's currently disabled.
   *
   * @param project The project to run the default action for.
   * @return The default action.
   */
  @Nullable
  public AnAction createDefaultAction(@NotNull Project project) {
    return null;
  }
}
