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
package com.intellij.psi.filters.getters;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.TypedLookupItem;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
class ClassLiteralLookupElement extends LookupElement implements TypedLookupItem {
  @NonNls private static final String DOT_CLASS = ".class";
  @Nullable private final SmartPsiElementPointer<PsiClass> myClass;
  private final PsiExpression myExpr;
  private final String myPresentableText;
  private final String myCanonicalText;

  ClassLiteralLookupElement(PsiClassType type, PsiElement context) {
    PsiClass psiClass = PsiUtil.resolveClassInType(type);
    myClass = psiClass == null ? null : SmartPointerManager.createPointer(psiClass);
    
    myCanonicalText = type.getCanonicalText();
    myPresentableText = type.getPresentableText();
    myExpr = JavaPsiFacade.getInstance(context.getProject()).getElementFactory().createExpressionFromText(myCanonicalText + DOT_CLASS, context);
  }

  @NotNull
  @Override
  public String getLookupString() {
    return myPresentableText + ".class";
  }

  @Override
  public void renderElement(LookupElementPresentation presentation) {
    presentation.setItemText(getLookupString());
    presentation.setIcon(myExpr.getIcon(0));
    String pkg = StringUtil.getPackageName(myCanonicalText);
    if (StringUtil.isNotEmpty(pkg)) {
      presentation.setTailText(" (" + pkg + ")", true);
    }
  }

  @Nullable
  @Override
  public PsiElement getPsiElement() {
    return myClass == null ? null : myClass.getElement();
  }

  @NotNull
  @Override
  public Object getObject() {
    return myExpr;
  }

  @Override
  public PsiType getType() {
    return myExpr.getType();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ClassLiteralLookupElement)) return false;

    return myCanonicalText.equals(((ClassLiteralLookupElement)o).myCanonicalText);
  }

  @Override
  public int hashCode() {
    return myCanonicalText.hashCode();
  }

  @Override
  public void handleInsert(InsertionContext context) {
    final Document document = context.getEditor().getDocument();
    document.replaceString(context.getStartOffset(), context.getTailOffset(), myCanonicalText + DOT_CLASS);
    final Project project = context.getProject();
    PsiDocumentManager.getInstance(project).commitDocument(document);
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(context.getFile(), context.getStartOffset(), context.getTailOffset());
  }
}
