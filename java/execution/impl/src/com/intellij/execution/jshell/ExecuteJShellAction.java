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
package com.intellij.execution.jshell;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.DocumentUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene Zhuravlev
 * Date: 06-Jun-17
 */
class ExecuteJShellAction extends AnAction{
  private static final AnAction ourInstance = new ExecuteJShellAction();
  private boolean myIsExecuteContextElement = false;

  private ExecuteJShellAction() {
    super(AllIcons.Toolwindows.ToolWindowRun);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) {
      return;
    }
    final Editor editor = CommonDataKeys.EDITOR.getData(e.getDataContext());
    if (editor == null) {
      return;
    }
    final VirtualFile vFile = CommonDataKeys.VIRTUAL_FILE.getData(e.getDataContext());
    if (vFile == null) {
      return;
    }

    FileDocumentManager.getInstance().saveAllDocuments();

    try {
      JShellHandler handler = JShellHandler.getAssociatedHandler(vFile);
      if (handler == null) {
        final SnippetEditorDecorator.ConfigurationPane config = SnippetEditorDecorator.getJShellConfiguration(e.getDataContext());
        final Module module = config != null ? config.getContextModule() : null;
        final Sdk sdk = config != null ? config.getRuntimeSdk() : null;
        handler = JShellHandler.create(project, vFile, module, sdk);
      }
      if (handler != null) {
        handler.toFront();
        boolean hasDataToEvaluate = false;

        final Document document = editor.getDocument();
        final TextRange selectedRange = EditorUtil.getSelectionInAnyMode(editor);
        if (selectedRange.isEmpty()) {
          final PsiElement snippet = getSnippetFromContext(project, e);
          if (snippet instanceof PsiJShellFile) {
            for (PsiElement element : ((PsiJShellFile)snippet).getExecutableSnippets()) {
              hasDataToEvaluate |= scheduleEval(handler, element.getText());
            }
          }
          else if (snippet != null){
            hasDataToEvaluate = scheduleEval(handler, snippet.getText());
          }
        }
        else {
          hasDataToEvaluate = scheduleEval(handler, document.getText(selectedRange));
        }
        if (!hasDataToEvaluate) {
          JShellDiagnostic.notifyInfo("Nothing to execute", project);
        }
      }
    }
    catch (Exception ex) {
      JShellDiagnostic.notifyError(ex, project);
    }
  }

  private static boolean scheduleEval(@NotNull JShellHandler handler, final String code) {
    if (!StringUtil.isEmptyOrSpaces(code)) {
      handler.evaluate(code.trim());
      return true;
    }
    return false;
  }

  @Nullable
  private PsiElement getSnippetFromContext(Project project, AnActionEvent e) {
    final Editor editor = e.getData(CommonDataKeys.EDITOR);
    if (editor != null) {
      final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      if (file instanceof PsiJShellFile) {

        PsiElement element = myIsExecuteContextElement? getContextElement(file, editor.getDocument(), editor.getCaretModel().getOffset()) : null;
        while (element != null) {
          final PsiElement parent = element.getParent();
          if (parent instanceof PsiJShellHolderMethod && element instanceof PsiEmptyStatement) {
            element = parent.getPrevSibling();  // skipping empty statements and
            if (element instanceof PsiJShellSyntheticElement) {
              element = element.getFirstChild();
              break;
            }
          }
          else {
            if (parent instanceof PsiJShellSyntheticElement || parent instanceof PsiJShellFile) {
              break;
            }
            element = parent;
          }
        }
        
        return element != null? element : file;
      }
    }
    return null;
  }

  private static PsiElement getContextElement(PsiFile file, final Document doc, final int offset) {
    final int begin = DocumentUtil.getLineStartOffset(offset, doc);
    PsiElement result = null;
    for (int off = offset; off >= begin; off--) {
      result = file.findElementAt(off);
      if (result != null && !(result instanceof PsiWhiteSpace)) {
        break;
      }
    }
    return result;
  }

  public static AnAction getSharedInstance() {
    return ourInstance;
  }
}
