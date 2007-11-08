/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.actions;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.RequestHint;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.containers.OrderedSet;
import com.intellij.util.text.CharArrayUtil;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collections;
import java.util.List;

public class SmartStepIntoAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project != null) {
      final DebuggerContextImpl debuggerContext = (DebuggerManagerEx.getInstanceEx(project)).getContext();
      doStep(project, debuggerContext.getSourcePosition(), debuggerContext.getDebuggerSession());
    }
  }

  
  private static void doStep(final Project project, final SourcePosition position, final DebuggerSession session) {
    final VirtualFile file = position.getFile().getVirtualFile();
    final FileEditor fileEditor = file != null? FileEditorManager.getInstance(project).getSelectedEditor(file) : null;
    if (fileEditor instanceof TextEditor) {
      final List<PsiMethod> methods = findReferencedMethods(position);
      if (methods.size() > 0) {
        if (methods.size() == 1) {
          session.stepInto(false, createSmartStepFilter(methods.get(0), session));
        }
        else {
          final PsiMethodListPopupStep popupStep = new PsiMethodListPopupStep(methods, new PsiMethodListPopupStep.OnChooseRunnable() {
            public void execute(PsiMethod chosenMethod) {
              session.stepInto(false, createSmartStepFilter(chosenMethod, session));
            }
          });
          final ListPopup popup = JBPopupFactory.getInstance().createListPopup(popupStep);
          final RelativePoint point = calcPopupLocation(((TextEditor)fileEditor).getEditor(), position);
          popup.show(point);
        }
        return;
      }
    }
    session.stepInto(false, null);
  }

  @Nullable
  private static RequestHint.SmartStepFilter createSmartStepFilter(final PsiMethod method, final DebuggerSession session) {
    try {
      return new RequestHint.SmartStepFilter(method, session.getProcess());
    }
    catch (EvaluateException e) {
      return null;
    }
  }


  private static RelativePoint calcPopupLocation(Editor editor, SourcePosition position) {
    Point p = editor.logicalPositionToXY(new LogicalPosition(position.getLine() + 1, 0));

    final Rectangle visibleArea = editor.getScrollingModel().getVisibleArea();
    if (!visibleArea.contains(p)) {
      p = new Point((visibleArea.x + visibleArea.width) / 2, (visibleArea.y + visibleArea.height) / 2);
    }
    return new RelativePoint(editor.getContentComponent(), p);
  }

  private static List<PsiMethod> findReferencedMethods(final SourcePosition position) {
    final PsiFile file = position.getFile();
    final Document doc = FileDocumentManager.getInstance().getDocument(file.getVirtualFile());
    final int line = position.getLine();

    final int startOffset = doc.getLineStartOffset(line);
    final TextRange lineRange = new TextRange(startOffset, doc.getLineEndOffset(line));
    final int offset = CharArrayUtil.shiftForward(doc.getCharsSequence(), startOffset, " \t");
    PsiElement element = file.findElementAt(offset);
    if (element != null) {
      do {
        final PsiElement parent = element.getParent();
        if (parent == null || (parent.getTextOffset() < lineRange.getStartOffset())) {
          break;
        }
        element = parent;
      }
      while(true);

      //noinspection unchecked
      final List<PsiMethod> methods = new OrderedSet<PsiMethod>(TObjectHashingStrategy.CANONICAL);
      final PsiRecursiveElementVisitor methodCollector = new PsiRecursiveElementVisitor() {
        public void visitAnonymousClass(PsiAnonymousClass aClass) { /*skip annonymous classes*/ }

        public void visitStatement(PsiStatement statement) {
          if (lineRange.intersects(statement.getTextRange())) {
            super.visitStatement(statement);
          }
        }

        public void visitCallExpression(final PsiCallExpression expression) {
          final PsiMethod psiMethod = expression.resolveMethod();
          if (psiMethod != null) {
            methods.add(psiMethod);
          }
          super.visitCallExpression(expression);
        }
      };
      element.accept(methodCollector);
      for (PsiElement sibling = element.getNextSibling(); sibling != null; sibling = sibling.getNextSibling()) {
        if (!lineRange.intersects(sibling.getTextRange())) {
          break;
        }
        sibling.accept(methodCollector);
      }
      return methods;
    }
    return Collections.emptyList();
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    Project project = event.getData(PlatformDataKeys.PROJECT);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    final DebuggerContextImpl context = (DebuggerManagerEx.getInstanceEx(project)).getContext();
    DebuggerSession debuggerSession = context.getDebuggerSession();
    final boolean isPaused = debuggerSession != null && debuggerSession.isPaused();
    final SuspendContextImpl suspendContext = context.getSuspendContext();
    final boolean hasCurrentThread = suspendContext != null && suspendContext.getThread() != null;
    presentation.setEnabled(isPaused && hasCurrentThread);
  }
}
