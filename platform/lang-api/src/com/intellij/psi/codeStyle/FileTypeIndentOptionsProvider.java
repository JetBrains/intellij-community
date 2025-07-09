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

package com.intellij.psi.codeStyle;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Allows to specify indent options for specific file types as opposed to languages. For a language it is highly recommended to use
 * {@code LanguageCodeStyleSettingsProvider}.
 * @see LanguageCodeStyleSettingsProvider
 * @see CodeStyleSettings#getIndentOptions(FileType)
 */
public interface FileTypeIndentOptionsProvider extends FileTypeIndentOptionsFactory {
  ExtensionPointName<FileTypeIndentOptionsProvider> EP_NAME = ExtensionPointName.create("com.intellij.fileTypeIndentOptionsProvider");

  @Override
  @NotNull
  CommonCodeStyleSettings.IndentOptions createIndentOptions();

  @Override
  @NotNull
  FileType getFileType();

  @NonNls
  String getPreviewText();

  void prepareForReformat(final PsiFile psiFile);
}