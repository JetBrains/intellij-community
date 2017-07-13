/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.propertyBased;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author peter
 */
class RehighlightAllEditors implements MadTestingAction {
  private final Project myProject;

  RehighlightAllEditors(Project project) {
    myProject = project;
  }

  @Override
  public void performAction() {
    for (FileEditor editor : FileEditorManager.getInstance(myProject).getAllEditors()) {
      if (editor instanceof TextEditor) {
        highlightEditor(((TextEditor)editor).getEditor(), myProject);
      }
    }
  }

  @Override
  public String toString() {
    return "RehighlightAllEditors";
  }

  @NotNull
  static List<HighlightInfo> highlightEditor(Editor editor, Project project) {
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    return CodeInsightTestFixtureImpl.instantiateAndRun(file, editor, new int[0], false);
  }
}
