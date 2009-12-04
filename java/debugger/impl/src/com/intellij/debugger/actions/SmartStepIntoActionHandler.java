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
package com.intellij.debugger.actions;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.RequestHint;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Document;
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
import com.intellij.xdebugger.impl.actions.DebuggerActionHandler;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class SmartStepIntoActionHandler extends DebuggerActionHandler {
  public void perform(@NotNull final Project project, final AnActionEvent event) {
    final DebuggerContextImpl debuggerContext = (DebuggerManagerEx.getInstanceEx(project)).getContext();
    doStep(project, debuggerContext.getSourcePosition(), debuggerContext.getDebuggerSession());
  }

  
  private static void doStep(final @NotNull Project project, final @Nullable SourcePosition position, final @NotNull DebuggerSession session) {
    final VirtualFile file = position != null ? position.getFile().getVirtualFile() : null;
    final FileEditor fileEditor = file != null? FileEditorManager.getInstance(project).getSelectedEditor(file) : null;
    if (fileEditor instanceof TextEditor) {
      final List<PsiMethod> methods = findReferencedMethods(position);
      if (methods.size() > 0) {
        if (methods.size() == 1) {
          session.stepInto(true, createSmartStepFilter(methods.get(0)));
        }
        else {
          final PsiMethodListPopupStep popupStep = new PsiMethodListPopupStep(methods, new PsiMethodListPopupStep.OnChooseRunnable() {
            public void execute(PsiMethod chosenMethod) {
              session.stepInto(true, createSmartStepFilter(chosenMethod));
            }
          });
          final ListPopup popup = JBPopupFactory.getInstance().createListPopup(popupStep);
          final RelativePoint point = DebuggerUIUtil.calcPopupLocation(((TextEditor)fileEditor).getEditor(), position.getLine());
          popup.show(point);
        }
        return;
      }
    }
    session.stepInto(true, null);
  }

  @Nullable
  private static RequestHint.SmartStepFilter createSmartStepFilter(final PsiMethod method) {
    return new RequestHint.SmartStepFilter(method);
  }


  private static List<PsiMethod> findReferencedMethods(final SourcePosition position) {
    final int line = position.getLine();
    if (line < 0) {
      return Collections.emptyList(); // the document has been changed
    }

    final PsiFile file = position.getFile();
    final VirtualFile vFile = file.getVirtualFile();
    if (vFile == null) {
      // the file is not physical
      return Collections.emptyList();
    }
    
    final Document doc = FileDocumentManager.getInstance().getDocument(vFile);
    
    final int startOffset = doc.getLineStartOffset(line);
    final TextRange lineRange = new TextRange(startOffset, doc.getLineEndOffset(line));
    final int offset = CharArrayUtil.shiftForward(doc.getCharsSequence(), startOffset, " \t");
    PsiElement element = file.findElementAt(offset);
    if (element != null && !(element instanceof PsiCompiledElement)) {
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
      final PsiElementVisitor methodCollector = new JavaRecursiveElementWalkingVisitor() {
        @Override public void visitAnonymousClass(PsiAnonymousClass aClass) { /*skip annonymous classes*/ }

        @Override public void visitStatement(PsiStatement statement) {
          if (lineRange.intersects(statement.getTextRange())) {
            super.visitStatement(statement);
          }
        }

        @Override public void visitCallExpression(final PsiCallExpression expression) {
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

  public boolean isEnabled(@NotNull final Project project, final AnActionEvent event) {
    final DebuggerContextImpl context = (DebuggerManagerEx.getInstanceEx(project)).getContext();
    DebuggerSession debuggerSession = context.getDebuggerSession();
    final boolean isPaused = debuggerSession != null && debuggerSession.isPaused();
    final SuspendContextImpl suspendContext = context.getSuspendContext();
    final boolean hasCurrentThread = suspendContext != null && suspendContext.getThread() != null;
    return isPaused && hasCurrentThread;
  }
}
