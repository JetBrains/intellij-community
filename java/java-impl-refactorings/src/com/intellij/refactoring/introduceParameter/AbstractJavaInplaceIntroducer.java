// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.introduceParameter;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.introduce.inplace.AbstractInplaceIntroducer;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractJavaInplaceIntroducer extends AbstractInplaceIntroducer<PsiVariable, PsiExpression> {
  protected TypeSelectorManagerImpl myTypeSelectorManager;

  public AbstractJavaInplaceIntroducer(final Project project,
                                       Editor editor,
                                       PsiExpression expr,
                                       PsiVariable localVariable,
                                       PsiExpression[] occurrences,
                                       TypeSelectorManagerImpl typeSelectorManager,
                                       @NlsContexts.Command String title) {
    super(project, getEditor(editor, expr), expr, localVariable, occurrences, title, JavaFileType.INSTANCE);
    myTypeSelectorManager = typeSelectorManager;
  }

  private static Editor getEditor(Editor editor, PsiExpression expr) {
    return expr != null && Comparing.equal(InjectedLanguageManager.getInstance(expr.getProject()).getTopLevelFile(expr), expr.getContainingFile())
           ? InjectedLanguageUtil.getTopLevelEditor(editor)
           : editor;
  }

  protected abstract PsiVariable createFieldToStartTemplateOn(String[] names, PsiType psiType);
  protected abstract String[] suggestNames(PsiType defaultType, String propName);
  protected abstract VariableKind getVariableKind();



  @Override
  protected String[] suggestNames(boolean replaceAll, PsiVariable variable) {
    final PsiType defaultType = getType();
    final String propertyName = variable != null
                                ? JavaCodeStyleManager.getInstance(myProject).variableNameToPropertyName(variable.getName(), VariableKind.LOCAL_VARIABLE)
                                : null;
    final String[] names = suggestNames(defaultType, propertyName);
    if (propertyName != null && names.length > 1) {
      final JavaCodeStyleManager javaCodeStyleManager = JavaCodeStyleManager.getInstance(myProject);
      final String paramName = javaCodeStyleManager.propertyNameToVariableName(propertyName, getVariableKind());
      final int idx = ArrayUtil.find(names, paramName);
      if (idx > -1) {
        ArrayUtil.swap(names, 0, idx);
      }
    }
    return names;
  }

  @Override
  protected PsiVariable createFieldToStartTemplateOn(boolean replaceAll, String[] names) {
    myTypeSelectorManager.setAllOccurrences(replaceAll);
    return createFieldToStartTemplateOn(names, getType());
  }

  @Override
  protected void correctExpression() {
    final PsiElement parent = getExpr().getParent();
    if (parent instanceof PsiExpressionStatement && parent.getLastChild() instanceof PsiErrorElement) {
      myExpr = ((PsiExpressionStatement)WriteAction
        .compute(() -> parent.replace(JavaPsiFacade.getElementFactory(myProject).createStatementFromText(parent.getText() + ";", parent)))).getExpression();
      //postfix templates start introduce in write action so postprocess reformatting aspect doesn't run automatically on write action finish
      PsiDocumentManager.getInstance(myProject).doPostponedOperationsAndUnblockDocument(myEditor.getDocument());
      myEditor.getCaretModel().moveToOffset(myExpr.getTextRange().getStartOffset());
    }
  }

  @Override
  public PsiExpression restoreExpression(PsiFile containingFile, PsiVariable psiVariable, RangeMarker marker, String exprText) {
    return restoreExpression(containingFile, psiVariable, JavaPsiFacade.getElementFactory(myProject), marker, exprText);
  }

  @Override
  protected void restoreState(@NotNull PsiVariable psiField) {
    final SmartTypePointer typePointer = SmartTypePointerManager.getInstance(myProject).createSmartTypePointer(getType());
    super.restoreState(psiField);
    for (PsiExpression occurrence : myOccurrences) {
      if (!occurrence.isValid()) return;
    }
    try {
      myTypeSelectorManager = myExpr != null
                              ? new TypeSelectorManagerImpl(myProject, typePointer.getType(), myExpr, myOccurrences)
                              : new TypeSelectorManagerImpl(myProject, typePointer.getType(), myOccurrences);
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  @Override
  protected void saveSettings(@NotNull PsiVariable psiVariable) {
    TypeSelectorManagerImpl.typeSelected(psiVariable.getType(), getType());//myDefaultType.getType());
    myTypeSelectorManager = null;
  }

  public PsiType getType() {
    return myTypeSelectorManager.getDefaultType();
  }

  public static String[] appendUnresolvedExprName(String[] names, final PsiExpression expr) {
    if (expr instanceof PsiReferenceExpression && ((PsiReferenceExpression)expr).resolve() == null) {
      final String name = expr.getText();
      if (PsiNameHelper.getInstance(expr.getProject()).isIdentifier(name, LanguageLevel.HIGHEST)) {
        names = ArrayUtil.mergeArrays(new String[]{name}, names);
      }
    }
    return names;
  }

  @Nullable
  public static PsiExpression restoreExpression(PsiFile containingFile,
                                                PsiVariable psiVariable,
                                                PsiElementFactory elementFactory,
                                                RangeMarker marker, String exprText) {
    if (exprText == null) return null;
    if (psiVariable == null || !psiVariable.isValid()) return null;
    final PsiElement refVariableElement = containingFile.findElementAt(marker.getStartOffset());
    final PsiElement refVariableElementParent = refVariableElement != null ? refVariableElement.getParent() : null;
    PsiElement expression = refVariableElement instanceof PsiKeyword && refVariableElementParent instanceof PsiNewExpression
                               ? (PsiNewExpression)refVariableElementParent
                               : refVariableElementParent instanceof PsiParenthesizedExpression
                                 ? ((PsiParenthesizedExpression)refVariableElementParent).getExpression()
                                 : PsiTreeUtil.getParentOfType(refVariableElement, PsiJavaCodeReferenceElement.class);
    if (expression instanceof PsiJavaCodeReferenceElement && !(expression.getParent() instanceof PsiMethodCallExpression)) {
      final String referenceName = ((PsiJavaCodeReferenceElement)expression).getReferenceName();
      if (((PsiJavaCodeReferenceElement)expression).resolve() == psiVariable ||
          Comparing.strEqual(psiVariable.getName(), referenceName) ||
          Comparing.strEqual(exprText, referenceName)) {
        return (PsiExpression)expression.replace(elementFactory.createExpressionFromText(exprText, psiVariable));
      }
    }
    if (expression == null) {
      expression = PsiTreeUtil.getParentOfType(refVariableElement, PsiExpression.class);
    }
    while (expression instanceof PsiReferenceExpression || expression instanceof PsiMethodCallExpression) {
      final PsiElement parent = expression.getParent();
      if (parent instanceof PsiMethodCallExpression) {
        if (parent.getText().equals(exprText)) return (PsiExpression)parent;
      }
      if (parent instanceof PsiExpression) {
        expression = parent;
        if (expression.getText().equals(exprText)) {
          return (PsiExpression)expression;
        }
      } else if (expression instanceof PsiReferenceExpression) {
        return null;
      } else {
        break;
      }
    }
    if (expression instanceof PsiExpression && expression.isValid() && expression.getText().equals(exprText)) {
      return (PsiExpression)expression;
    }

    if (refVariableElementParent instanceof PsiExpression && refVariableElementParent.getText().equals(exprText)) {
      return (PsiExpression)refVariableElementParent;
    }

    if (expression == null &&
        refVariableElement instanceof PsiIdentifier &&
        refVariableElementParent instanceof PsiJavaCodeReferenceElement &&
        refVariableElement.getText().equals(psiVariable.getName())) {
      // E.g. "this.x y = z;" is parsed as two expression statements
      // but "a.x y = z;" is parsed as declaration of variable y of type a.x, 
      // so 'a' is not a reference to the variable 'a' but a type reference
      return (PsiExpression)refVariableElementParent.replace(elementFactory.createExpressionFromText(exprText, psiVariable));
    }

    return null;
  }

  protected String chooseName(String[] names, Language language) {
    String inputName = getInputName();
    if (inputName != null && !isIdentifier(inputName, language)) {
      inputName = null;
    }
    return inputName != null ? inputName : names[0];
  }
}
