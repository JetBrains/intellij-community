package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 *  @author dsl
 */
public class MakeTypeGenericAction extends PsiElementBaseIntentionAction {
  private String variableName;
  private String newTypeName;

  @NotNull
  public String getFamilyName() {
    return CodeInsightBundle.message("intention.make.type.generic.family");
  }

  @NotNull
  public String getText() {
    return CodeInsightBundle.message("intention.make.type.generic.text", variableName, newTypeName);
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, @Nullable PsiElement element) {
    if (element == null) return false;
    if (LanguageLevel.JDK_1_5.compareTo(PsiUtil.getLanguageLevel(element)) > 0) return false;
    if (!element.isWritable()) return false;
    return findVariable(element) != null;
  }

  private Pair<PsiVariable,PsiType> findVariable(final PsiElement element) {
    PsiVariable variable = null;
    if (element instanceof PsiIdentifier) {
      if (element.getParent() instanceof PsiVariable) {
        variable = (PsiVariable)element.getParent();
      }
    }
    else if (element instanceof PsiJavaToken) {
      final PsiJavaToken token = (PsiJavaToken)element;
      if (token.getTokenType() != JavaTokenType.EQ) return null;
      if (token.getParent() instanceof PsiVariable) {
        variable = (PsiVariable)token.getParent();
      }
    }
    if (variable == null) {
      return null;
    }
    variableName = variable.getName();
    final PsiExpression initializer = variable.getInitializer();
    if (initializer == null) return null;
    final PsiType variableType = variable.getType();
    final PsiType initializerType = initializer.getType();
    if (!(variableType instanceof PsiClassType)) return null;
    final PsiClassType variableClassType = (PsiClassType) variableType;
    if (!variableClassType.isRaw()) return null;
    if (!(initializerType instanceof PsiClassType)) return null;
    final PsiClassType initializerClassType = (PsiClassType) initializerType;
    if (initializerClassType.isRaw()) return null;
    final PsiClassType.ClassResolveResult variableResolveResult = variableClassType.resolveGenerics();
    final PsiClassType.ClassResolveResult initializerResolveResult = initializerClassType.resolveGenerics();
    if (initializerResolveResult.getElement() == null) return null;
    final PsiSubstitutor targetSubstitutor = TypeConversionUtil.getClassSubstitutor(variableResolveResult.getElement(), initializerResolveResult.getElement(), initializerResolveResult.getSubstitutor());
    if (targetSubstitutor == null) return null;
    PsiType type = variable.getManager().getElementFactory().createType(variableResolveResult.getElement(), targetSubstitutor);
    newTypeName = type.getCanonicalText();
    return Pair.create(variable, type);
  }

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final CaretModel caretModel = editor.getCaretModel();
    final int position = caretModel.getOffset();
    final PsiElement element = file.findElementAt(position);
    Pair<PsiVariable, PsiType> pair = findVariable(element);
    if (pair == null) return;
    PsiVariable variable = pair.getFirst();
    PsiType type = pair.getSecond();

    variable.getTypeElement().replace(variable.getManager().getElementFactory().createTypeElement(type));
  }
}
