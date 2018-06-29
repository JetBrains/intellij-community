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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.codeStyle.CommonCodeStyleSettings.IndentOptions;

/**
 * @author Rustam Vishnyakov
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
   * Sets the file as accepted by end user.
   * @param file The file to be accepted. A particular implementation of {@code FileIndentOptionsProvider} may ignore this parameter
   *             and set a global acceptance flag so that no notification will be shown anymore.
   */
  public void setAccepted(@SuppressWarnings("UnusedParameters") @NotNull VirtualFile file) {}

  public boolean areActionsAvailable(@NotNull VirtualFile file, @NotNull IndentOptions indentOptions) {
    return false;
  }

  @Nullable
  public AnAction[] getActions(@NotNull PsiFile file, @NotNull IndentOptions indentOptions) {
    return null;
  }

  public String getTooltip(@NotNull IndentOptions indentOptions) {
    return getDefaultTooltip(indentOptions);
  }

  public static String getDefaultTooltip(@NotNull IndentOptions indentOptions) {
    if (indentOptions.USE_TAB_CHARACTER) {
      return "Tab";
    }
    else {
      return Integer.toString(indentOptions.INDENT_SIZE) + " spaces";
    }
  }

}
