// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.jshell;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.util.DocumentUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene Zhuravlev
 */
final class ExecuteJShellAction extends AnAction{
  private static class Holder {
    private static final AnAction ourInstance = new ExecuteJShellAction();
  }
  private static final boolean myIsExecuteContextElement = false;

  private ExecuteJShellAction() {
    super(AllIcons.Toolwindows.ToolWindowRun);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) {
      return;
    }
    final Editor editor = e.getData(CommonDataKeys.EDITOR);
    if (editor == null) {
      return;
    }

    final FileEditor fileEditor = e.getData(PlatformCoreDataKeys.FILE_EDITOR);
    if (fileEditor == null) {
      return;
    }
    final SnippetEditorDecorator.ConfigurationPane config =
      SnippetEditorDecorator.ConfigurationPane.getJShellConfiguration(fileEditor);
    if (config == null) {
      return;
    }

    FileDocumentManager.getInstance().saveAllDocuments();

    try {
      JShellHandler handler = config.getJShellHandler();
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
        JShellDiagnostic.notifyInfo(JavaCompilerBundle.message("jshell.nothing.to.execute"), project);
      }
    }
    catch (Exception ex) {
      Logger.getInstance(ExecuteJShellAction.class).warn(ex);
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
  private static PsiElement getSnippetFromContext(Project project, @NotNull AnActionEvent e) {
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
    return Holder.ourInstance;
  }
}
