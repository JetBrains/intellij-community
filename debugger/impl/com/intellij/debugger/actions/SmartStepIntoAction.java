/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.actions;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.JVMName;
import com.intellij.debugger.engine.JVMNameUtil;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SmartStepIntoAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(DataKeys.PROJECT);
    if (project != null) {
      final DebuggerContextImpl debuggerContext = (DebuggerManagerEx.getInstanceEx(project)).getContext();
      doStep(project, debuggerContext.getSourcePosition(), debuggerContext.getDebuggerSession());
    }
  }

  
  private static void doStep(final Project project, final SourcePosition position, final DebuggerSession session) {
    final FileEditor fileEditor = FileEditorManager.getInstance(project).getSelectedEditor(position.getFile().getVirtualFile());
    if (fileEditor instanceof TextEditor) {
      final List<PsiMethod> methods = findReferencedMethods(position);
      if (methods.size() > 1) {
        final PsiMethodListPopupStep popupStep = new PsiMethodListPopupStep(methods, new PsiMethodListPopupStep.OnChooseRunnable() {
          public void execute(PsiMethod chosenMethod) {
            final String hintMethodSignature = createSteppingHintMethodSignature(chosenMethod, session.getProcess());
            session.stepInto(false, hintMethodSignature);
          }
        });
        final Editor editor = ((TextEditor)fileEditor).getEditor();
        JBPopupFactory.getInstance().createListPopup(popupStep).show(calcPopupLocation(editor, position));
        return;
      }
    }
    session.stepInto(false, null);
  }


  private static RelativePoint calcPopupLocation(Editor editor, SourcePosition position) {
    Point p = editor.visualPositionToXY(new VisualPosition(position.getLine() + 1, 0));

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

      final List<PsiMethod> methods = new ArrayList<PsiMethod>();
      final PsiRecursiveElementVisitor methodCollector = new PsiRecursiveElementVisitor() {
        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
          final PsiMethod psiMethod = expression.resolveMethod();
          if (psiMethod != null) {
            methods.add(psiMethod);
          }
          super.visitMethodCallExpression(expression);
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

  @Nullable
  private static String createSteppingHintMethodSignature(final PsiMethod psiMethod, final DebugProcessImpl debugProcess) {
    final JVMName clsSignature = JVMNameUtil.getJVMQualifiedName(psiMethod.getContainingClass());
    final JVMName methodSignature = JVMNameUtil.getJVMSignature(psiMethod);
    final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      //noinspection HardCodedStringLiteral
      builder.append("L").append(clsSignature.getName(debugProcess).replace('.', '/')).append(";");
      builder.append(".");
      builder.append(psiMethod.getName());
      builder.append(methodSignature.getName(debugProcess));
      return builder.toString();
    }
    catch (EvaluateException e) {
      return null;
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    Project project = event.getData(DataKeys.PROJECT);
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
