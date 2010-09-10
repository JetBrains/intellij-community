/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.refactoring.changeSignature;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

/**
 * User: anna
 * Date: Sep 9, 2010
 */
public class EditorEscapeHandler extends EditorActionHandler {
  private final EditorActionHandler myOriginalHandler;

  public EditorEscapeHandler(EditorActionHandler originalHandler) {
    myOriginalHandler = originalHandler;
  }

  @Override
  public void execute(Editor editor, DataContext dataContext) {
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    final PsiFile file = LangDataKeys.PSI_FILE.getData(dataContext);
    final ChangeSignatureGestureDetector detector = ChangeSignatureGestureDetector.getInstance(project);
    if (file != null && detector.containsChangeSignatureChange(file)) {
      detector.clearSignatureChange(file);
      DaemonCodeAnalyzer.getInstance(project).restart();
    }
    else {
      myOriginalHandler.execute(editor, dataContext);
    }
  }

  @Override
  public boolean isEnabled(Editor editor, DataContext dataContext) {
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project != null) {
      final PsiFile file = LangDataKeys.PSI_FILE.getData(dataContext);
      if (file != null && ChangeSignatureGestureDetector.getInstance(project).containsChangeSignatureChange(file)) {
        return true;
      }
    }
    return myOriginalHandler.isEnabled(editor, dataContext);
  }
}
