/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.debugger.SourcePosition;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.containers.OrderedSet;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Stack;

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.11.11
 */
public class JavaSmartStepIntoHandler extends JvmSmartStepIntoHandler {
  @Override
  public boolean isAvailable(final SourcePosition position) {
    final PsiFile file = position.getFile();
    return file.getLanguage().isKindOf(JavaLanguage.INSTANCE);
  }

  @Override
  @NotNull
  public List<StepTarget> findSmartStepTargets(final SourcePosition position) {
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
    if (doc == null) return Collections.emptyList();
    if (line >= doc.getLineCount()) {
      return Collections.emptyList(); // the document has been changed
    }
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
      final List<StepTarget> targets = new OrderedSet<StepTarget>();
      final PsiElementVisitor methodCollector = new JavaRecursiveElementVisitor() {
        final Stack<String> myParamNameStack = new Stack<String>();

        @Nullable
        private String getCurrentParamName() {
          return myParamNameStack.size() > 0? myParamNameStack.peek() : null;
        }

        @Override
        public void visitAnonymousClass(PsiAnonymousClass aClass) {
          for (PsiMethod psiMethod : aClass.getMethods()) {
            targets.add(new MethodTarget(psiMethod, getCurrentParamName(), psiMethod.getBody(), true));
          }
        }

        @Override
        public void visitStatement(PsiStatement statement) {
          if (lineRange.intersects(statement.getTextRange())) {
            super.visitStatement(statement);
          }
        }

        @Override
        public void visitCallExpression(final PsiCallExpression expression) {
          final PsiMethod psiMethod = expression.resolveMethod();
          if (psiMethod != null) {
            final PsiElement highlightElement = expression instanceof PsiMethodCallExpression?
              ((PsiMethodCallExpression)expression).getMethodExpression().getReferenceNameElement() : null;
            targets.add(new MethodTarget(psiMethod, null, highlightElement, false));
            final PsiExpressionList argList = expression.getArgumentList();
            if (argList != null) {
              final String methodName = psiMethod.getName();
              final PsiExpression[] expressions = argList.getExpressions();
              final PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
              for (int idx = 0; idx < expressions.length; idx++) {
                final String paramName = (idx < parameters.length && !parameters[idx].isVarArgs())? parameters[idx].getName() : "arg"+(idx+1);
                myParamNameStack.push(methodName + ": " + paramName);
                final PsiExpression argExpression = expressions[idx];
                try {
                  argExpression.accept(this);
                }
                finally {
                  myParamNameStack.pop();
                }
              }
            }
          }
          else {
            super.visitCallExpression(expression);
          }
        }

      };
      element.accept(methodCollector);
      for (PsiElement sibling = element.getNextSibling(); sibling != null; sibling = sibling.getNextSibling()) {
        if (!lineRange.intersects(sibling.getTextRange())) {
          break;
        }
        sibling.accept(methodCollector);
      }
      return targets;
    }
    return Collections.emptyList();
  }

  private static class MethodTarget implements StepTarget {
    private final PsiMethod myMethod;
    private final PsiElement myHighlightElement;
    private final String myLabel;
    private final boolean myNeedBreakpointRequest;

    private MethodTarget(@NotNull PsiMethod method, String currentParamName, PsiElement highlightElement, boolean needBreakpointRequest) {
      myMethod = method;
      myHighlightElement = highlightElement;
      myLabel = currentParamName == null? null : currentParamName + ".";
      myNeedBreakpointRequest = needBreakpointRequest;
    }

    @Nullable
    public PsiElement getHighlightElement() {
      return myHighlightElement;
    }

    @Nullable
    public String getMethodLabel() {
      return myLabel;
    }

    @NotNull
    public PsiMethod getMethod() {
      return myMethod;
    }

    public boolean needsBreakpointRequest() {
      return myNeedBreakpointRequest;
    }

    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      final MethodTarget that = (MethodTarget)o;

      if (!myMethod.equals(that.myMethod)) {
        return false;
      }

      return true;
    }

    public int hashCode() {
      return myMethod.hashCode();
    }
  }
}
