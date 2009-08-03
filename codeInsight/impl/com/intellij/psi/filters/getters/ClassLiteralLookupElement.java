package com.intellij.psi.filters.getters;

import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.*;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class ClassLiteralLookupElement extends LookupElement implements TypedLookupItem {
  @NonNls private static final String DOT_CLASS = ".class";
  private final PsiExpression myExpr;
  private final String myPresentableText;
  private final String myCanonicalText;

  public ClassLiteralLookupElement(PsiClassType type, PsiElement context) {
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
    final PsiType type = myExpr.getType();
    if (type != null) {
      presentation.setTypeText(type.getPresentableText());
    }
  }

  @Override
  public AutoCompletionPolicy getAutoCompletionPolicy() {
    return AutoCompletionPolicy.NEVER_AUTOCOMPLETE;
  }

  @NotNull
  @Override
  public Object getObject() {
    return myExpr;
  }

  @Override
  public InsertHandler<? extends LookupElement> getInsertHandler() {
    throw new UnsupportedOperationException("Method getInsertHandler is not yet implemented in " + getClass().getName());
  }

  @NotNull
  @Override
  protected LookupElementRenderer<? extends LookupElement> getRenderer() {
    throw new UnsupportedOperationException("Method getRenderer is not yet implemented in " + getClass().getName());
  }

  public PsiType getType() {
    return myExpr.getType();
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
