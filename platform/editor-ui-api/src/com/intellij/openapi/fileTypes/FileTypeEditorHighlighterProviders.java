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
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class FileTypeEditorHighlighterProviders extends FileTypeExtension<EditorHighlighterProvider> {
  public static final FileTypeEditorHighlighterProviders INSTANCE = new FileTypeEditorHighlighterProviders();

  private FileTypeEditorHighlighterProviders() {
    super("com.intellij.editorHighlighterProvider");
  }

  @NotNull
  @Override
  protected List<EditorHighlighterProvider> buildExtensions(@NotNull String stringKey, @NotNull final FileType key) {
    List<EditorHighlighterProvider> fromEP = super.buildExtensions(stringKey, key);
    if (fromEP.isEmpty()) {
      EditorHighlighterProvider defaultProvider = new EditorHighlighterProvider() {
        @Override
        public EditorHighlighter getEditorHighlighter(@Nullable Project project,
                                                      @NotNull FileType fileType,
                                                      @Nullable VirtualFile virtualFile,
                                                      @NotNull EditorColorsScheme colors) {
          return EditorHighlighterFactory.getInstance().createEditorHighlighter(
            SyntaxHighlighterFactory.getSyntaxHighlighter(fileType, project, virtualFile), colors);
        }
      };
      return Collections.singletonList(defaultProvider);
    }
    return fromEP;
  }
}
