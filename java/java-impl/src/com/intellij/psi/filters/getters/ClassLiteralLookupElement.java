// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.filters.getters;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.TypedLookupItem;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    myExpr = JavaPsiFacade.getElementFactory(context.getProject()).createExpressionFromText(myCanonicalText + DOT_CLASS, context);
  }

  @NotNull
  @Override
  public String getLookupString() {
    return myPresentableText + ".class";
  }

  @Override
  public void renderElement(@NotNull LookupElementPresentation presentation) {
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
  public void handleInsert(@NotNull InsertionContext context) {
    final Document document = context.getEditor().getDocument();
    int offset = context.getTailOffset();
    String replacement = myCanonicalText + DOT_CLASS;
    if (document.getTextLength() > offset + DOT_CLASS.length() &&
        document.getText(TextRange.from(offset, DOT_CLASS.length())).equals(DOT_CLASS)) {
      replacement = myCanonicalText;
    }
    document.replaceString(context.getStartOffset(), offset, replacement);
    final Project project = context.getProject();
    PsiDocumentManager.getInstance(project).commitDocument(document);
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(context.getFile(), context.getStartOffset(), offset);
  }
}
