/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui;

import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.util.Consumer;
import com.intellij.util.LocalTimeCounter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LanguageTextField extends EditorTextField {
  private final Language myLanguage;
  private final Project myProject;

  public LanguageTextField(Language language, @NotNull Project project, @NotNull String value) {
    this(language, project, value, null);
  }

  public LanguageTextField(@Nullable Language language,
                           @NotNull Project project,
                           @NotNull String value,
                           @Nullable Consumer<PsiFile> tagger) {
    super(createDocument(value, language, project, tagger), project,
          language != null ? language.getAssociatedFileType() : StdFileTypes.PLAIN_TEXT, language == null);

    myLanguage = language;
    myProject = project;

    setEnabled(language != null);

    ShiftTabAction.attachTo(this);
  }

  private static Document createDocument(String value, @Nullable Language language, Project project, @Nullable Consumer<PsiFile> tagger) {
    if (language != null) {
      final PsiFileFactory factory = PsiFileFactory.getInstance(project);
      final FileType fileType = language.getAssociatedFileType();
      assert fileType != null;

      final long stamp = LocalTimeCounter.currentTime();
      final PsiFile psiFile = factory.createFileFromText("Dummy." + fileType.getDefaultExtension(), fileType, value, stamp, true, false);
      if (tagger != null) {
        tagger.consume(psiFile);
      }
      final Document document = PsiDocumentManager.getInstance(project).getDocument(psiFile);
      assert document != null;
      return document;
    }
    else {
      return EditorFactory.getInstance().createDocument(value);
    }
  }

  protected EditorEx createEditor() {
    final EditorEx ex = super.createEditor();

    if (myLanguage != null) {
      final FileType fileType = myLanguage.getAssociatedFileType();
      ex.setHighlighter(HighlighterFactory.createHighlighter(myProject, fileType));
    }
    ex.setEmbeddedIntoDialogWrapper(true);

    return ex;
  }
}
