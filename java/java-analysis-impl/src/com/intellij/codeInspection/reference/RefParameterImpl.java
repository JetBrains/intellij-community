// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.reference;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RefParameterImpl extends RefJavaElementImpl implements RefParameter {
  private static final int USED_FOR_READING_MASK = 0x10000;
  private static final int USED_FOR_WRITING_MASK = 0x20000;


  private final short myIndex;
  private Object myActualValueTemplate;

  RefParameterImpl(PsiParameter parameter, int index, RefManager manager) {
    super(parameter, manager);

    myIndex = (short)index;
    myActualValueTemplate = VALUE_UNDEFINED;
    final RefElementImpl owner = (RefElementImpl)manager.getReference(PsiTreeUtil.getParentOfType(parameter, PsiMethod.class));
    if (owner != null) {
      owner.add(this);
    }
  }

  @Override
  public void parameterReferenced(boolean forWriting) {
    if (forWriting) {
      setUsedForWriting();
    } else {
      setUsedForReading();
    }
  }

  @Override
  public boolean isUsedForReading() {
    return checkFlag(USED_FOR_READING_MASK);
  }

  private void setUsedForReading() {
    setFlag(true, USED_FOR_READING_MASK);
  }

  @Override
  public PsiParameter getElement() {
    return (PsiParameter)super.getElement();
  }

  @Override
  public boolean isUsedForWriting() {
    return checkFlag(USED_FOR_WRITING_MASK);
  }

  private void setUsedForWriting() {
    setFlag(true, USED_FOR_WRITING_MASK);
  }

  @Override
  public void accept(@NotNull final RefVisitor visitor) {
    if (visitor instanceof RefJavaVisitor) {
      ApplicationManager.getApplication().runReadAction(() -> ((RefJavaVisitor)visitor).visitParameter(this));
    } else {
      super.accept(visitor);
    }
  }

  @Override
  public int getIndex() {
    return myIndex;
  }

  @Override
  public void buildReferences() {
    final RefJavaUtil refUtil = RefJavaUtil.getInstance();
    final PsiParameter parameter = getElement();
    if (parameter != null) {
      refUtil.addReferences(parameter, this, parameter.getModifierList());
    }
  }

  void updateTemplateValue(PsiExpression expression) {
    if (myActualValueTemplate == VALUE_IS_NOT_CONST) return;

    Object newTemplate = getExpressionValue(expression);
    if (myActualValueTemplate == VALUE_UNDEFINED) {
      myActualValueTemplate = newTemplate;
    }
    else if (!Comparing.equal(myActualValueTemplate, newTemplate)) {
      myActualValueTemplate = VALUE_IS_NOT_CONST;
    }
  }

  @Nullable
  @Override
  public Object getActualConstValue() {
    return myActualValueTemplate;
  }

  @Override
  protected void initialize() {
  }

  @Override
  public String getExternalName() {
    final String[] result = new String[1];
    final Runnable runnable = () -> {
      PsiParameter parameter = getElement();
      LOG.assertTrue(parameter != null);
      result[0] = PsiFormatUtil.getExternalName(parameter);
    };

    ApplicationManager.getApplication().runReadAction(runnable);

    return result[0];
  }

  @Nullable
  public static Object getExpressionValue(PsiExpression expression) {
    if (expression instanceof PsiReferenceExpression) {
      PsiReferenceExpression referenceExpression = (PsiReferenceExpression) expression;
      PsiElement resolved = referenceExpression.resolve();
      if (resolved instanceof PsiField) {
        PsiField psiField = (PsiField) resolved;
        if (psiField.hasModifierProperty(PsiModifier.STATIC) &&
            psiField.hasModifierProperty(PsiModifier.FINAL) &&
            psiField.getContainingClass().getQualifiedName() != null) {
          return PsiFormatUtil.formatVariable(psiField, PsiFormatUtilBase.SHOW_NAME |
                                                        PsiFormatUtilBase.SHOW_CONTAINING_CLASS |
                                                        PsiFormatUtilBase.SHOW_FQ_NAME,
                                              PsiSubstitutor.EMPTY);
        }
      }
    }
    if (expression instanceof PsiLiteralExpression && ((PsiLiteralExpression)expression).getValue() == null) {
      return null;
    }
    Object constValue = JavaConstantExpressionEvaluator.computeConstantExpression(expression, false);
    return constValue == null ? VALUE_IS_NOT_CONST : constValue;
  }

  @Nullable
  static RefElement parameterFromExternalName(final RefManager manager, final String fqName) {
    final int idx = fqName.lastIndexOf(' ');
    if (idx > 0) {
      final String method = fqName.substring(0, idx);
      final RefMethod refMethod = RefMethodImpl.methodFromExternalName(manager, method);
      if (refMethod != null) {
        final PsiMethod element = (PsiMethod)refMethod.getElement();
        final PsiParameterList list = element.getParameterList();
        final PsiParameter[] parameters = list.getParameters();
        int paramIdx = 0;
        final String paramName = fqName.substring(idx + 1);
        for (PsiParameter parameter : parameters) {
          final String name = parameter.getName();
          if (name != null && name.equals(paramName)) {
            return manager.getExtension(RefJavaManager.MANAGER).getParameterReference(parameter, paramIdx);
          }
          paramIdx++;
        }
      }
    }
    return null;
  }
}
