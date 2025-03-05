// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  public static final ExtensionPointName<FileIndentOptionsProvider> EP_NAME = ExtensionPointName.create("com.intellij.fileIndentOptionsProvider");

  /**
   * @deprecated Use {@link #getIndentOptions(Project, CodeStyleSettings, VirtualFile)}
   */
  @Deprecated
  public @Nullable IndentOptions getIndentOptions(@NotNull CodeStyleSettings settings, @NotNull PsiFile file) {
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

  public @Nullable CodeStyleStatusBarUIContributor getIndentStatusBarUiContributor(@NotNull IndentOptions indentOptions) {
    return null;
  }

  public final boolean isAllowed(boolean isFullReformat) {
    return !isFullReformat || useOnFullReformat();
  }
}
