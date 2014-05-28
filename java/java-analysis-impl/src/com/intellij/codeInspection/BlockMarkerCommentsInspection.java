/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInspection;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Batkovich
 */
public class BlockMarkerCommentsInspection extends BaseJavaBatchLocalInspectionTool {
  private static final String END_WORD = "end";

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new PsiElementVisitor() {
      @Override
      public void visitElement(final PsiElement element) {
        if (!(element instanceof PsiComment)) {
          return;
        }
        final IElementType tokenType = ((PsiComment)element).getTokenType();
        if (!(tokenType.equals(JavaTokenType.END_OF_LINE_COMMENT))) {
          return;
        }
        final String commentText = element.getText().substring(2).trim().toLowerCase();
        if (!commentText.startsWith(END_WORD)) {
          return;
        }
        if (isMethodBlockMarker(element) ||
            isLoopOrIfBlockMarker(element) ||
            isClassBlockMarker(element) ||
            isAnonymousClass(element) ||
            isTryCatchFinallyBlockMarker(element)) {
          holder.registerProblem(element, "", new LocalQuickFix() {
            @NotNull
            @Override
            public String getName() {
              return getFamilyName();
            }

            @NotNull
            @Override
            public String getFamilyName() {
              return "Remove block marker comments";
            }

            @Override
            public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
              element.delete();
            }
          });
        }
      }
    };
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return "Block marker comment";
  }

  private static boolean isAnonymousClass(final PsiElement comment) {
    final PsiElement parent = comment.getParent();
    if (parent == null || !(parent instanceof PsiDeclarationStatement)) {
      return false;
    }
    final PsiLocalVariable localVariable = PsiTreeUtil.getPrevSiblingOfType(comment, PsiLocalVariable.class);
    if (localVariable == null) {
      return false;
    }
    final PsiNewExpression newExpression = PsiTreeUtil.getChildOfType(localVariable, PsiNewExpression.class);
    if (newExpression == null) {
      return false;
    }
    return PsiTreeUtil.getChildOfType(newExpression, PsiAnonymousClass.class) != null;
  }

  private static boolean isClassBlockMarker(final PsiElement comment) {
    final PsiElement parent = comment.getParent();
    if (parent == null || !(parent instanceof PsiClass)) {
        return false;
    }
    final PsiJavaToken token = PsiTreeUtil.getPrevSiblingOfType(comment, PsiJavaToken.class);
    return token != null && JavaTokenType.RBRACE.equals(token.getTokenType());
  }

  private static boolean isTryCatchFinallyBlockMarker(final PsiElement comment) {
    final PsiElement parent = comment.getParent();
    if (parent == null || !(parent instanceof PsiTryStatement)) {
      return false;
    }
    return PsiTreeUtil.getPrevSiblingOfType(comment, PsiCodeBlock.class) != null ||
           PsiTreeUtil.getPrevSiblingOfType(comment, PsiCatchSection.class) != null;
  }

  private static boolean isMethodBlockMarker(final PsiElement comment) {
    final PsiCodeBlock codeBlock = PsiTreeUtil.getPrevSiblingOfType(comment, PsiCodeBlock.class);
    if (codeBlock == null) {
      return false;
    }
    final PsiElement parent = comment.getParent();
    return parent != null && parent instanceof PsiMethod;
  }

  private static boolean isLoopOrIfBlockMarker(final PsiElement comment) {
    final PsiCodeBlock codeBlock = PsiTreeUtil.getPrevSiblingOfType(comment, PsiCodeBlock.class);
    if (codeBlock == null) {
      return false;
    }
    final PsiElement mayBeBlockStatement = comment.getParent();
    if (mayBeBlockStatement == null || !(mayBeBlockStatement instanceof PsiBlockStatement)) {
      return false;
    }
    final PsiElement parent = mayBeBlockStatement.getParent();
    return parent != null && (parent instanceof PsiLoopStatement || parent instanceof PsiIfStatement);
  }
}
