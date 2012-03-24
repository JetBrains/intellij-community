/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.resolve.reference.impl;

import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.JavaLookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class JavaLangClassMemberReference extends PsiReferenceBase<PsiLiteralExpression> implements InsertHandler<LookupElement> {
  private final PsiClassObjectAccessExpression myContext;

  public JavaLangClassMemberReference(PsiLiteralExpression literal, PsiClassObjectAccessExpression context) {
    super(literal);
    myContext = context;
  }

  @Override
  public PsiElement resolve() {
    final String name =  (String)getElement().getValue();
    final Type type = getType();

    if (type != null) {
      final PsiClass psiClass = getPsiClass();
      if (psiClass != null) {
        PsiMember member;
        if (type == Type.FIELD || type == Type.DECLARED_FIELD) {
          member = psiClass.findFieldByName(name, false);
        } else {
          final PsiMethod[] methods = psiClass.findMethodsByName(name, false);
          member = methods.length == 0 ? null : methods[0];
        }

        return member;
      }
    }

    return null;
  }

  @Nullable
  private PsiClass getPsiClass() {
    return PsiTypesUtil.getPsiClass(myContext.getOperand().getType());
  }

  @Nullable
  private Type getType() {
    boolean selfFound = false;
    for (PsiElement child : myContext.getParent().getChildren()) {
      if (!selfFound) {
        if (child == myContext) {
          selfFound = true;
        }
        continue;
      }

      if (child instanceof PsiIdentifier) {
        return Type.fromString(child.getText());
      }
    }
    return null;
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    final Type type = getType();
    final PsiClass psiClass = getPsiClass();
    if (psiClass != null && type != null) {
      if (type == Type.DECLARED_FIELD) {
        return psiClass.getFields();
      } else if (type == Type.DECLARED_METHOD) {
        final List<LookupElementBuilder> elements = new ArrayList<LookupElementBuilder>();
        for (PsiMethod method : psiClass.getMethods()) {
          elements.add(JavaLookupElementBuilder.forMethod(method, PsiSubstitutor.EMPTY).setInsertHandler(this));
        }
        return elements.toArray();
      }
    }
    return EMPTY_ARRAY;
  }

  @Override
  public void handleInsert(InsertionContext context, LookupElement item) {
    final Object object = item.getObject();
    if (object instanceof PsiMethod) {
      final PsiElement newElement = PsiUtilCore.getElementAtOffset(context.getFile(), context.getStartOffset());
      final int start = newElement.getTextRange().getEndOffset();
      final PsiElement params = newElement.getParent().getParent();
      final int end = params.getTextRange().getEndOffset() - 1;
      final String types = getMethodTypes((PsiMethod)object);
      context.getDocument().replaceString(start, end, types);
      context.commitDocument();
      final PsiElement firstParam = PsiUtilCore.getElementAtOffset(context.getFile(), context.getStartOffset());
      final PsiMethodCallExpression methodCall = PsiTreeUtil.getParentOfType(firstParam, PsiMethodCallExpression.class);
      if (methodCall != null) {
        JavaCodeStyleManager.getInstance(context.getProject()).shortenClassReferences(methodCall);
      }
    }
  }

  private static String getMethodTypes(PsiMethod method) {
    final StringBuilder buf = new StringBuilder();
    for (PsiParameter parameter : method.getParameterList().getParameters()) {
      buf.append(", ").append(parameter.getType().getCanonicalText()).append(".class");
    }
    return buf.toString();
  }


  enum Type {
    FIELD, DECLARED_FIELD, METHOD, DECLARED_METHOD;

    @Nullable
    static Type fromString(String s) {
      if ("getField".equals(s)) return FIELD;
      if ("getDeclaredField".equals(s)) return DECLARED_FIELD;
      if ("getMethod".equals(s)) return METHOD;
      if ("getDeclaredMethod".equals(s)) return DECLARED_METHOD;
      return null;
    }
  }
}
