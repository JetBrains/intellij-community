/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Oct 21, 2001
 * Time: 4:35:07 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.reference;

import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import org.jetbrains.annotations.Nullable;

public class RefParameterImpl extends RefElementImpl implements RefParameter {
  private static final int USED_FOR_READING_MASK = 0x10000;
  private static final int USED_FOR_WRITING_MASK = 0x20000;
  private static final String VALUE_UNDEFINED = "#";

  private final short myIndex;
  private String myActualValueTemplate;

  RefParameterImpl(PsiParameter parameter, int index, RefManager manager) {
    super(parameter, manager);

    myIndex = (short)index;
    myActualValueTemplate = VALUE_UNDEFINED;
  }

  public void parameterReferenced(boolean forWriting) {
    if (forWriting) {
      setUsedForWriting();
    } else {
      setUsedForReading();
    }
  }

  public boolean isUsedForReading() {
    return checkFlag(USED_FOR_READING_MASK);
  }

  private void setUsedForReading() {
    setFlag(true, USED_FOR_READING_MASK);
  }

  public PsiParameter getElement() {
    return (PsiParameter)super.getElement();
  }

  public boolean isUsedForWriting() {
    return checkFlag(USED_FOR_WRITING_MASK);
  }

  private void setUsedForWriting() {
    setFlag(true, USED_FOR_WRITING_MASK);
  }

  public void accept(RefVisitor visitor) {
    visitor.visitParameter(this);
  }

  public int getIndex() {
    return myIndex;
  }

  public void updateTemplateValue(PsiExpression expression) {
    if (myActualValueTemplate == null) return;

    String newTemplate = null;
    if (expression instanceof PsiLiteralExpression) {
      PsiLiteralExpression psiLiteralExpression = (PsiLiteralExpression) expression;
      newTemplate = psiLiteralExpression.getText();
    } else if (expression instanceof PsiReferenceExpression) {
      PsiReferenceExpression referenceExpression = (PsiReferenceExpression) expression;
      PsiElement resolved = referenceExpression.resolve();
      if (resolved instanceof PsiField) {
        PsiField psiField = (PsiField) resolved;
        if (psiField.hasModifierProperty(PsiModifier.STATIC) &&
            psiField.hasModifierProperty(PsiModifier.FINAL) &&
            psiField.getContainingClass().getQualifiedName() != null) {
          newTemplate = PsiFormatUtil.formatVariable(psiField, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_CONTAINING_CLASS | PsiFormatUtil.SHOW_FQ_NAME, PsiSubstitutor.EMPTY);
        }
      }
    }

    if (myActualValueTemplate == VALUE_UNDEFINED) {
      myActualValueTemplate = newTemplate;
    } else if (!Comparing.equal(myActualValueTemplate, newTemplate)) {
      myActualValueTemplate = null;
    }
  }

  public String getActualValueIfSame() {
    if (myActualValueTemplate == VALUE_UNDEFINED) return null;
    return myActualValueTemplate;
  }

  protected void initialize() {
    ((RefManagerImpl)getRefManager()).fireNodeInitialized(this);
  }

  @Nullable
  public static RefElement parameterFromExternalName(final RefManager manager, final String fqName) {
    final int idx = fqName.lastIndexOf('.');
    if (idx > 0) {
      final String paramName = fqName.substring(idx + 1);
      final String method = fqName.substring(0, idx);
      final RefMethod refMethod = RefMethodImpl.methodFromExternalName(manager, method);
      if (refMethod != null) {
        final PsiMethod element = (PsiMethod)refMethod.getElement();
        final PsiParameterList list = element.getParameterList();
        final PsiParameter[] parameters = list.getParameters();
        int paramIdx = 0;
        for (PsiParameter parameter : parameters) {
          final String name = parameter.getName();
          if (name != null && name.equals(paramName)) {
            return manager.getParameterReference(parameter, paramIdx);
          }
          paramIdx++;
        }
      }
    }
    return null; 
  }
}
