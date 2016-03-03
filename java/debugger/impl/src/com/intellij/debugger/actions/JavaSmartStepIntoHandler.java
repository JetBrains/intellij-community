/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.DocumentUtil;
import com.intellij.util.Range;
import com.intellij.util.containers.OrderedSet;
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
  public List<SmartStepTarget> findSmartStepTargets(final SourcePosition position) {
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
    TextRange curLineRange = DocumentUtil.getLineTextRange(doc, line);
    PsiElement element = position.getElementAt();
    PsiElement body = DebuggerUtilsEx.getBody(DebuggerUtilsEx.getContainingMethod(element));
    final TextRange lineRange = (body != null) ? curLineRange.intersection(body.getTextRange()) : curLineRange;

    if (lineRange == null || lineRange.isEmpty()) {
      return Collections.emptyList();
    }

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
      final List<SmartStepTarget> targets = new OrderedSet<>();

      final Ref<TextRange> textRange = new Ref<>(lineRange);

      final PsiElementVisitor methodCollector = new JavaRecursiveElementVisitor() {
        final Stack<PsiMethod> myContextStack = new Stack<>();
        final Stack<String> myParamNameStack = new Stack<>();
        private int myNextLambdaExpressionOrdinal = 0;
        private boolean myInsideLambda = false;

        @Nullable
        private String getCurrentParamName() {
          return myParamNameStack.isEmpty() ? null : myParamNameStack.peek();
        }

        @Override
        public void visitAnonymousClass(PsiAnonymousClass aClass) {
          for (PsiMethod psiMethod : aClass.getMethods()) {
            targets.add(0, new MethodSmartStepTarget(psiMethod, getCurrentParamName(), psiMethod.getBody(), true, null));
          }
        }

        public void visitLambdaExpression(PsiLambdaExpression expression) {
          boolean inLambda = myInsideLambda;
          myInsideLambda = true;
          super.visitLambdaExpression(expression);
          myInsideLambda = inLambda;
          targets.add(0, new LambdaSmartStepTarget(expression, getCurrentParamName(), expression.getBody(), myNextLambdaExpressionOrdinal++, null));
        }

        @Override
        public void visitMethodReferenceExpression(PsiMethodReferenceExpression expression) {
          PsiElement element = expression.resolve();
          if (element instanceof PsiMethod) {
            PsiElement navMethod = element.getNavigationElement();
            if (navMethod instanceof PsiMethod) {
              targets.add(0, new MethodSmartStepTarget(((PsiMethod)navMethod), null, expression, true, null));
            }
          }
        }

        @Override
        public void visitField(PsiField field) {
          TextRange range = field.getTextRange();
          if (lineRange.intersects(range)) {
            //textRange.set(textRange.get().union(range));
            super.visitField(field);
          }
        }

        @Override
        public void visitMethod(PsiMethod method) {
          TextRange range = method.getTextRange();
          if (lineRange.intersects(range)) {
            //textRange.set(textRange.get().union(range));
            super.visitMethod(method);
          }
        }

        @Override
        public void visitStatement(PsiStatement statement) {
          TextRange range = statement.getTextRange();
          if (lineRange.intersects(range)) {
            textRange.set(textRange.get().union(range));
            super.visitStatement(statement);
          }
        }

        @Override
        public void visitExpression(PsiExpression expression) {
          TextRange range = expression.getTextRange();
          if (lineRange.intersects(range)) {
            textRange.set(textRange.get().union(range));
          }
          super.visitExpression(expression);
        }

        public void visitExpressionList(PsiExpressionList expressionList) {
          final PsiMethod psiMethod = myContextStack.isEmpty()? null : myContextStack.peek();
          if (psiMethod != null) {
            final String methodName = psiMethod.getName();
            final PsiExpression[] expressions = expressionList.getExpressions();
            final PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
            for (int idx = 0; idx < expressions.length; idx++) {
              final String paramName = (idx < parameters.length && !parameters[idx].isVarArgs())? parameters[idx].getName() : "arg"+(idx+1);
              myParamNameStack.push(methodName + ": " + paramName + ".");
              final PsiExpression argExpression = expressions[idx];
              try {
                argExpression.accept(this);
              }
              finally {
                myParamNameStack.pop();
              }
            }
          }
          else {
            super.visitExpressionList(expressionList);
          }
        }

        @Override
        public void visitCallExpression(final PsiCallExpression expression) {
          final PsiMethod psiMethod = expression.resolveMethod();
          if (psiMethod != null) {
            myContextStack.push(psiMethod);
            targets.add(new MethodSmartStepTarget(
              psiMethod,
              null,
              expression instanceof PsiMethodCallExpression?
                ((PsiMethodCallExpression)expression).getMethodExpression().getReferenceNameElement()
                : expression instanceof PsiNewExpression? ((PsiNewExpression)expression).getClassOrAnonymousClassReference() : expression,
              myInsideLambda,
              null
            ));
          }
          try {
            super.visitCallExpression(expression);
          }
          finally {
            if (psiMethod != null) {
              myContextStack.pop();
            }
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

      Range<Integer> lines =
        new Range<>(doc.getLineNumber(textRange.get().getStartOffset()), doc.getLineNumber(textRange.get().getEndOffset()));
      for (SmartStepTarget target : targets) {
        target.setCallingExpressionLines(lines);
      }
      return targets;
    }
    return Collections.emptyList();
  }
}
