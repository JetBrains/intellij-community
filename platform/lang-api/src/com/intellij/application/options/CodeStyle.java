// Copyright 2000-2017 JetBrains s.r.o.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.intellij.application.options;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.CustomCodeStyleSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utility class for miscellaneous code style settings retrieving methods.
 */
public class CodeStyle {

  private CodeStyle() {
  }

  /**
   * @return Default application-wide root code style settings.
   */
  @NotNull
  public static CodeStyleSettings getDefaultSettings() {
    //noinspection deprecation
    return CodeStyleSettingsManager.getInstance().getCurrentSettings();
  }

  /**
   * Returns root code style settings for the given project. For configurable language settings use {@link #getLanguageSettings(PsiFile)} or
   * {@link #getLanguageSettings(PsiFile, Language)}.
   * @param project The project to get code style settings for.
   * @return The current root code style settings associated with the project.
   */
  @NotNull
  public static CodeStyleSettings getSettings(@NotNull Project project) {
    return CodeStyleSettingsManager.getInstance(project).getCurrentSettings();
  }

  /**
   * Returns root code style settings for the given PSI file. For configurable language settings use {@link #getLanguageSettings(PsiFile)} or
   * {@link #getLanguageSettings(PsiFile, Language)}.
   * @param file The file to get code style settings for.
   * @return The current root code style settings associated with the file or default settings if the file is invalid.
   */
  @NotNull
  public static CodeStyleSettings getSettings(@NotNull PsiFile file) {
    if (file.isValid()) {
      Project project = file.getProject();
      return CodeStyleSettingsManager.getInstance(project).getCurrentSettings();
    }
    return getDefaultSettings();
  }


  public static CodeStyleSettings getSettings(@NotNull Editor editor) {
    Project project = editor.getProject();
    if (project != null) {
      PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      if (file != null) {
        return getSettings(file);
      }
      return getSettings(project);
    }
    return getDefaultSettings();
  }

  /**
   * Returns language code style settings for the specified editor. If the editor has an associated document and PSI file, returns
   * settings for the PSI file or {@code null} otherwise.
   *
   * @param editor The editor to get settings for.
   * @return The language code style settings for the editor or {@code null}.
   */
  @Nullable
  public static CommonCodeStyleSettings getLanguageSettings(@NotNull Editor editor) {
    Project project = editor.getProject();
    if (project != null) {
      PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      if (file != null) {
        return getLanguageSettings(file);
      }
    }
    return null;
  }

  /**
   * Returns custom settings for the given PSI file.
   * @param file The file.
   * @param customSettingsClass The class of a settings object to be returned.
   * @param <T> Settings class type.
   * @return The current custom settings associated with the PSI file.
   */
  @NotNull
  public static <T extends CustomCodeStyleSettings> T getCustomSettings(@NotNull PsiFile file, Class<T> customSettingsClass) {
    CodeStyleSettings rootSettings = getSettings(file);
    return rootSettings.getCustomSettings(customSettingsClass);
  }

  /**
   * Returns language settings for the given PSI file. The language is taken from the file itself.
   * @param file The file to retrieve language settings for.
   * @return The associated language settings.
   */
  @NotNull
  public static CommonCodeStyleSettings getLanguageSettings(@NotNull PsiFile file) {
    CodeStyleSettings rootSettings = getSettings(file);
    return rootSettings.getCommonSettings(file.getLanguage());
  }

  /**
   * Returns language settings for the given PSI file and language. This method may be useful when PSI file contains elements for multiple
   * languages and language settings should be taken from a specific language context.
   * @param file The file to retrieve language settings for.
   * @return The associated language settings.
   */
  @NotNull
  public static CommonCodeStyleSettings getLanguageSettings(@NotNull PsiFile file, @NotNull Language language) {
    CodeStyleSettings rootSettings = getSettings(file);
    return rootSettings.getCommonSettings(language);
  }

  /**
   * Returns indent options for the given PSI file. The method attempts to use {@link com.intellij.psi.codeStyle.FileIndentOptionsProvider}
   * if applicable to the file. If there are no suitable indent options providers, it takes configurable language indent options or
   * retrieves indent options by file type.
   * @param file The file to get indent options for.
   * @return The file indent options.
   * @see com.intellij.psi.codeStyle.FileIndentOptionsProvider
   */
  @NotNull
  public static CommonCodeStyleSettings.IndentOptions getIndentOptions(@NotNull PsiFile file) {
    CodeStyleSettings rootSettings = getSettings(file);
    return rootSettings.getIndentOptionsByFile(file);
  }

  /**
   * Explicitly retrieves indent options by file type.
   * @param file The file to get indent options for.
   * @return The indent options associated with the file type.
   */
  @NotNull
  public static CommonCodeStyleSettings.IndentOptions getIndentOptionsByFileType(@NotNull PsiFile file) {
    return getSettings(file).getIndentOptions(file.getFileType());
  }

  /**
   * Returns indent options for the given project and document.
   * @param project The current project.
   * @param document The document to get indent options for.
   * @return The indent options associated with document's PSI file if the file is available or other indent options otherwise.
   */
  @NotNull
  public static CommonCodeStyleSettings.IndentOptions getIndentOptions(@NotNull Project project, @NotNull Document document) {
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
    if (file != null) {
      return getIndentOptions(file);
    }
    return getSettings(project).getIndentOptions(null);
  }

  /**
   * Returns indent size for the given PSI file.
   * @param file The file to get indent size for.
   * @return The indent size to be used with the PSI file.
   */
  public static int getIndentSize(@NotNull PsiFile file) {
    return getIndentOptions(file).INDENT_SIZE;
  }

}
