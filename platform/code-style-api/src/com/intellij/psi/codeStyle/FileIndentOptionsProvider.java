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

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.modifier.CodeStyleStatusBarUIContributor;
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
   * @deprecated Use {@link #getIndentOptions(Project, CodeStyleSettings, VirtualFile)}
   */
  @Deprecated
  @Nullable
  public IndentOptions getIndentOptions(@NotNull CodeStyleSettings settings, @NotNull PsiFile file) {
    return null;
  }

  /**
   * Retrieves indent options for a virtual file within a given project.
   * @param project The current project.
   * @param settings Code style settings for which indent options are calculated.
   * @param file The file to retrieve options for.
   * @return Indent options or {@code null} if the provider is not applicable.
   */
  public @Nullable IndentOptions getIndentOptions(@NotNull Project project, @NotNull CodeStyleSettings settings, @NotNull VirtualFile file) {
    Document document = FileDocumentManager.getInstance().getCachedDocument(file);
    if (document != null) {
      PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
      if (psiFile != null) {
        return getIndentOptions(settings, psiFile);
      }
    }
    return null;
  }

  /**
   * Tells if the provider can be used when a complete file is reformatted.
   * @return True by default
   */
  public boolean useOnFullReformat() {
    return true;
  }

  protected static void notifyIndentOptionsChanged(@NotNull Project project, @NotNull VirtualFile virtualFile) {
    CodeStyleSettingsManager.getInstance(project).fireCodeStyleSettingsChanged(virtualFile);
  }

  protected static void notifyIndentOptionsChanged(@NotNull Project project) {
    CodeStyleSettingsManager.getInstance(project).fireCodeStyleSettingsChanged();
  }

  @Nullable
  public CodeStyleStatusBarUIContributor getIndentStatusBarUiContributor(@NotNull IndentOptions indentOptions) {
    return null;
  }

  public final boolean isAllowed(boolean isFullReformat) {
    return !isFullReformat || useOnFullReformat();
  }
}
