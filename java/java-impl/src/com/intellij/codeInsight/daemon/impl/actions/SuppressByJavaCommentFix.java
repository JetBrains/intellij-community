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
package com.intellij.codeInsight.daemon.impl.actions;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.SuppressManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.jsp.jspJava.JspMethodCall;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class SuppressByJavaCommentFix extends SuppressByCommentFix {
  public SuppressByJavaCommentFix(HighlightDisplayKey key) {
    super(key, PsiStatement.class);
  }

  @Nullable
  protected PsiElement getContainer(PsiElement context) {
    if (context == null || PsiTreeUtil.getParentOfType(context, JspMethodCall.class) != null) return null;
    return PsiTreeUtil.getParentOfType(context, PsiStatement.class, false);
  }

  @Override
  protected void createSuppression(final Project project,
                                   final Editor editor,
                                   final PsiElement element,
                                   final PsiElement container) throws IncorrectOperationException {
    boolean added = false;
    if (container instanceof PsiDeclarationStatement && SuppressManager.getInstance().canHave15Suppressions(element)) {
      final PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)container;
      final PsiElement[] declaredElements = declarationStatement.getDeclaredElements();
      for (PsiElement declaredElement : declaredElements) {
        if (declaredElement instanceof PsiLocalVariable) {
          final PsiModifierList modifierList = ((PsiLocalVariable)declaredElement).getModifierList();
          if (modifierList != null) {
            SuppressFix.addSuppressAnnotation(project, editor, container, (PsiLocalVariable)declaredElement, myID);
            added = true;
            break;
          }
        }
      }
    }
    if (!added) {
      super.createSuppression(project, editor, element, container);
    }
  }
}
