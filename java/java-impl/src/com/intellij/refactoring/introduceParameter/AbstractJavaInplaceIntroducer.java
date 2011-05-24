package com.intellij.refactoring.introduceParameter;

import com.intellij.codeInsight.intention.impl.TypeExpression;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.*;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nullable;

/**
 * User: anna
 */
public abstract class AbstractJavaInplaceIntroducer extends AbstractInplaceIntroducer<PsiVariable, PsiExpression> {
  protected SmartTypePointer myTypePointer;
  protected TypeSelectorManagerImpl myTypeSelectorManager;
  protected final SmartTypePointer myDefaultType;
  protected final TypeExpression myExpression;


  public AbstractJavaInplaceIntroducer(Project project,
                                       Editor editor,
                                       PsiExpression expr,
                                       PsiVariable localVariable,
                                       PsiExpression[] occurrences,
                                       PsiType defaultType, TypeSelectorManagerImpl typeSelectorManager, String title) {
    super(project, editor, expr, localVariable, occurrences, title);
    myTypeSelectorManager = typeSelectorManager;
    myExpression = new TypeExpression(project, typeSelectorManager.getTypesForOne());
    myDefaultType = SmartTypePointerManager.getInstance(project).createSmartTypePointer(defaultType);
    setAdvertisementText(getAdvertisementText(myExpression.hasSuggestions()));
  }

  protected abstract PsiVariable createFieldToStartTemplateOn(String[] names, PsiType psiType);
  protected abstract String[] suggestNames(PsiType defaultType, String propName);


  @Override
  protected String[] suggestNames(boolean replaceAll, PsiVariable variable) {
    myTypeSelectorManager.setAllOccurences(replaceAll);
    final PsiType defaultType = myTypeSelectorManager.getTypeSelector().getSelectedType();
    final String propertyName = variable != null
                                ? JavaCodeStyleManager.getInstance(myProject).variableNameToPropertyName(variable.getName(), VariableKind.LOCAL_VARIABLE)
                                : null;
    return suggestNames(defaultType, propertyName);
  }


  @Override
  protected PsiVariable createFieldToStartTemplateOn(boolean replaceAll, String[] names) {
    final PsiType fieldDefaultType = myTypePointer != null ? myTypePointer.getType() : null;

    myTypeSelectorManager.setAllOccurences(replaceAll);
    PsiType defaultType = myTypeSelectorManager.getTypeSelector().getSelectedType();
    if (fieldDefaultType != null) {
      if (replaceAll) {
        if (ArrayUtil.find(myTypeSelectorManager.getTypesForAll(), fieldDefaultType) != -1) {
          defaultType = fieldDefaultType;
        }
      }
      else if (ArrayUtil.find(myTypeSelectorManager.getTypesForOne(), fieldDefaultType) != -1) {
        defaultType = fieldDefaultType;
      }
    }
    return createFieldToStartTemplateOn(names, defaultType);
  }

  @Override
  public PsiExpression restoreExpression(PsiFile containingFile, PsiVariable psiVariable, RangeMarker marker, String exprText) {
    return restoreExpression(containingFile, psiVariable, JavaPsiFacade.getElementFactory(myProject), marker, exprText);
  }

  @Override
  protected void restoreState(PsiVariable psiField) {
    myTypePointer = SmartTypePointerManager.getInstance(myProject).createSmartTypePointer(psiField.getType());
    super.restoreState(psiField);
    myTypeSelectorManager = new TypeSelectorManagerImpl(myProject, myDefaultType.getType(), null, myExpr, myOccurrences);
  }

  @Override
  protected void saveSettings(PsiVariable psiVariable) {
    TypeSelectorManagerImpl.typeSelected(psiVariable.getType(), myDefaultType.getType());
  }

  protected void addAdditionalVariables(TemplateBuilderImpl builder) {
    final PsiTypeElement typeElement = getVariable().getTypeElement();
    builder.replaceElement(typeElement, "Variable_Type", createExpression(myExpression, typeElement.getText()), true, true);
  }

  @Nullable
  public static PsiExpression restoreExpression(PsiFile containingFile,
                                                PsiVariable psiVariable,
                                                PsiElementFactory elementFactory,
                                                RangeMarker marker, String exprText) {
    if (exprText == null) return null;
    if (psiVariable == null || !psiVariable.isValid()) return null;
    final PsiElement refVariableElement = containingFile.findElementAt(marker.getStartOffset());
    PsiExpression expression = PsiTreeUtil.getParentOfType(refVariableElement, PsiReferenceExpression.class);
    if (expression instanceof PsiReferenceExpression && (((PsiReferenceExpression)expression).resolve() == psiVariable ||
                                                         Comparing.strEqual(psiVariable.getName(),
                                                                            ((PsiReferenceExpression)expression).getReferenceName()))) {
      return (PsiExpression)expression.replace(elementFactory.createExpressionFromText(exprText, psiVariable));
    }
    if (expression == null) {
      expression = PsiTreeUtil.getParentOfType(refVariableElement, PsiExpression.class);
    }
    return expression != null && expression.isValid() && expression.getText().equals(exprText) ? expression : null;
  }

  @Nullable
   private static String getAdvertisementText(final boolean hasTypeSuggestion) {
     final Keymap keymap = KeymapManager.getInstance().getActiveKeymap();
     if (hasTypeSuggestion) {
       final Shortcut[] shortcuts = keymap.getShortcuts("PreviousTemplateVariable");
       if (shortcuts.length > 0) {
         return "Press " + shortcuts[0] + " to change type";
       }
     }
     return null;
   }

   public static Expression createExpression(final TypeExpression expression, final String defaultType) {
     return new Expression() {
       @Override
       public Result calculateResult(ExpressionContext context) {
         return new TextResult(defaultType);
       }

       @Override
       public Result calculateQuickResult(ExpressionContext context) {
         return new TextResult(defaultType);
       }

       @Override
       public LookupElement[] calculateLookupItems(ExpressionContext context) {
         return expression.calculateLookupItems(context);
       }

       @Override
       public String getAdvertisingText() {
         return null;
       }
     };
   }

}
