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
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.DocumentUtil;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene Zhuravlev
 * Date: 06-Jun-17
 */
class ExecuteJShellAction extends AnAction{
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.jshell.ExecuteJShellAction");
  private static final AnAction ourInstance = new ExecuteJShellAction();

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

    final Document document = editor.getDocument();
    final TextRange selectedRange = EditorUtil.getSelectionInAnyMode(editor);
    String code = null;
    if (selectedRange.isEmpty()) {
      final PsiElement snippet = getSnippetFromContext(project, e);
      if (snippet != null) {
        code = snippet.getText();
      }
    }
    else {
      code = document.getText(selectedRange);
    }

    if (StringUtil.isEmptyOrSpaces(code))  {
      JShellDiagnostic.notifyInfo("Nothing to execute", project);
      return;
    }

    try {
      JShellHandler handler = JShellHandler.getAssociatedHandler(vFile);
      if (handler == null) {
        handler = JShellHandler.create(project, vFile, e.getData(LangDataKeys.MODULE));
      }
      if (handler != null) {
        handler.toFront();
        handler.evaluate(code);
      }
    }
    catch (Exception ex) {
      JShellDiagnostic.notifyError(ex, project);
    }
  }

  @Nullable
  private static PsiElement getSnippetFromContext(Project project, AnActionEvent e) {
    final Editor editor = e.getData(CommonDataKeys.EDITOR);
    if (editor != null) {
      final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      if (file instanceof PsiJShellFile) {

        final PsiElement context = getContextElement(file, editor.getDocument(), editor.getCaretModel().getOffset());
        for (PsiElement element = context; element != null; element = element.getParent()) {
          if (element instanceof PsiJShellImportHolder || element instanceof PsiJShellHolderMethod) {
            return element;
          }
          if (element instanceof PsiMember && ((PsiMember)element).getContainingClass() instanceof PsiJShellRootClass) {
            return element;
          }
          if (element instanceof PsiClass) {
            final PsiClass containingClass = ((PsiClass)element).getContainingClass();
            if (containingClass == null || containingClass instanceof PsiJShellRootClass) {
              return element;
            }
          }
        }
        
        return file;
      }
    }
    return null;
  }

  private static PsiElement getContextElement(PsiFile file, final Document doc, final int offset) {
    final int begin = DocumentUtil.getLineStartOffset(offset, doc);
    final int end = DocumentUtil.getLineEndOffset(offset, doc);
    PsiElement result = null;
    for (int off = begin; off <= end; off++) {
      result = file.findElementAt(off);
      if (result != null) {
        break;
      }
    }
    while (result instanceof PsiWhiteSpace) {
      final PsiElement next = result.getNextSibling();
      if (next == null || next.getTextOffset() > end) {
        break;
      }
      result = next;
    }

    while (result != null) {
      final PsiElement parent = result.getParent();
      if (parent instanceof PsiJShellSyntheticElement) {
        break;
      }
      result = parent;
    }

    return result;
  }

  public static AnAction getSharedInstance() {
    return ourInstance;
  }
}
