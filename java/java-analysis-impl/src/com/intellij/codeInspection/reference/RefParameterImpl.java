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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Oct 21, 2001
 * Time: 4:35:07 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.reference;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RefParameterImpl extends RefJavaElementImpl implements RefParameter {
  private static final int USED_FOR_READING_MASK = 0x10000;
  private static final int USED_FOR_WRITING_MASK = 0x20000;
  private static final String VALUE_UNDEFINED = "#";

  private final short myIndex;
  private String myActualValueTemplate;

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

  @Override
  public String getActualValueIfSame() {
    if (myActualValueTemplate == VALUE_UNDEFINED) return null;
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
  public static RefElement parameterFromExternalName(final RefManager manager, final String fqName) {
    final int idx = fqName.lastIndexOf(' ');
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
            return manager.getExtension(RefJavaManager.MANAGER).getParameterReference(parameter, paramIdx);
          }
          paramIdx++;
        }
      }
    }
    return null;
  }

  @Nullable
  public static PsiParameter findPsiParameter(String fqName, final PsiManager manager) {
    final int idx = fqName.lastIndexOf(' ');
    if (idx > 0) {
      final String paramName = fqName.substring(idx + 1);
      final String method = fqName.substring(0, idx);
      final PsiMethod psiMethod = RefMethodImpl.findPsiMethod(manager, method);
      if (psiMethod != null) {
        final PsiParameterList list = psiMethod.getParameterList();
        final PsiParameter[] parameters = list.getParameters();
        for (PsiParameter parameter : parameters) {
          final String name = parameter.getName();
          if (name != null && name.equals(paramName)) {
            return parameter;
          }
        }
      }
    }
    return null;
  }
}
