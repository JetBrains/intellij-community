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
import com.intellij.refactoring.introduce.inplace.AbstractInplaceIntroducer;
import com.intellij.refactoring.ui.TypeSelector;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * User: anna
 */
public abstract class AbstractJavaInplaceIntroducer extends AbstractInplaceIntroducer<PsiVariable, PsiExpression> {
  protected TypeSelectorManagerImpl myTypeSelectorManager;
  protected TypeSelector myTypeSelector;

  public AbstractJavaInplaceIntroducer(Project project,
                                       Editor editor,
                                       PsiExpression expr,
                                       PsiVariable localVariable,
                                       PsiExpression[] occurrences,
                                       TypeSelectorManagerImpl typeSelectorManager, String title) {
    super(project, editor, expr, localVariable, occurrences, title);
    myTypeSelectorManager = typeSelectorManager;
    myTypeSelector = myTypeSelectorManager.getTypeSelector();
    JComponent component = myTypeSelector.getComponent();
    if (component instanceof JCheckBox) {
      ((JCheckBox)component).addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          getVariable().getTypeElement().replace(JavaPsiFacade.getElementFactory(myProject).createTypeElement(myTypeSelector.getSelectedType()));
        }
      });
    }
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
    myTypeSelectorManager.setAllOccurences(replaceAll);
    return createFieldToStartTemplateOn(names, myTypeSelector.getSelectedType());
  }

  @Override
  public PsiExpression restoreExpression(PsiFile containingFile, PsiVariable psiVariable, RangeMarker marker, String exprText) {
    return restoreExpression(containingFile, psiVariable, JavaPsiFacade.getElementFactory(myProject), marker, exprText);
  }

  @Override
  protected void restoreState(PsiVariable psiField) {
    super.restoreState(psiField);
  }

  @Override
  protected void saveSettings(PsiVariable psiVariable) {
    TypeSelectorManagerImpl.typeSelected(psiVariable.getType(), myTypeSelectorManager.getDefaultType());//myDefaultType.getType());
  }

  protected PsiType getType() {
    return myTypeSelector.getSelectedType();
  }

  protected JComponent typeComponent() {
    JComponent component = myTypeSelector.getComponent();
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(new JLabel("Type: "), BorderLayout.WEST);
    panel.add(component, BorderLayout.CENTER);
    return panel;
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
