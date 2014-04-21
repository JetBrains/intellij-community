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
package com.intellij.codeInsight.unwrap;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;

import java.util.List;

public class JavaAnonymousUnwrapper extends JavaUnwrapper {
  public JavaAnonymousUnwrapper() {
    super(CodeInsightBundle.message("unwrap.anonymous"));
  }

  @Override
  public boolean isApplicableTo(PsiElement e) {
    return e instanceof PsiAnonymousClass
           && ((PsiAnonymousClass)e).getMethods().length <= 1;
  }

  @Override
  public PsiElement collectAffectedElements(PsiElement e, List<PsiElement> toExtract) {
    super.collectAffectedElements(e, toExtract);
    return findElementToExtractFrom(e);
  }

  @Override
  protected void doUnwrap(PsiElement element, Context context) throws IncorrectOperationException {
    PsiElement from = findElementToExtractFrom(element);

    final PsiMethod[] methods = ((PsiAnonymousClass)element).getMethods();

    if (methods.length == 1) {
      final PsiCodeBlock body = methods[0].getBody();
      if (body != null) {
        final PsiStatement[] statements = body.getStatements();
        if (statements.length == 1 && statements[0] instanceof PsiReturnStatement) {
          final PsiExpression returnValue = ((PsiReturnStatement)statements[0]).getReturnValue();
          if (from instanceof PsiDeclarationStatement) {
            final PsiElement[] declaredElements = ((PsiDeclarationStatement)from).getDeclaredElements();
            if (declaredElements.length == 1 && declaredElements[0] instanceof PsiVariable) {
              context.setInitializer((PsiVariable)declaredElements[0], returnValue);
              return;
            }
          }
        }
      }
    }

    for (PsiMethod m : methods) {
      context.extractFromCodeBlock(m.getBody(), from);
    }

    PsiElement next = from.getNextSibling();
    if (next instanceof PsiJavaToken && ((PsiJavaToken)next).getTokenType() == JavaTokenType.SEMICOLON) {
      context.deleteExactly(from.getNextSibling());
    }
    context.deleteExactly(from);
  }

  private static PsiElement findElementToExtractFrom(PsiElement el) {
    if (el.getParent() instanceof PsiNewExpression) el = el.getParent();
    el = findTopmostParentOfType(el, PsiMethodCallExpression.class);
    el = findTopmostParentOfType(el, PsiAssignmentExpression.class);
    el = findTopmostParentOfType(el, PsiDeclarationStatement.class);
    
    PsiElement parent = el.getParent();
    while (parent instanceof PsiExpressionStatement || parent instanceof PsiReturnStatement) {
      el = parent;
      parent = el.getParent();
    }

    return el;
  }

  private static PsiElement findTopmostParentOfType(PsiElement el, Class<? extends PsiElement> clazz) {
    while (true) {
      @SuppressWarnings({"unchecked"})
      PsiElement temp = PsiTreeUtil.getParentOfType(el, clazz, true, PsiAnonymousClass.class);
      if (temp == null || temp instanceof PsiFile) return el;
      el = temp;
    }
  }
}