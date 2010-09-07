/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.refactoring.changeSignature;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import org.jetbrains.annotations.Nullable;

public class JavaParameterTableModel extends ParameterTableModelBase<ParameterInfoImpl> {
  private static final Logger LOG = Logger.getInstance(JavaParameterTableModel.class.getName());

  private final Project myProject;

  public JavaParameterTableModel(PsiElement typeContext,
                                 PsiElement defaultValueContext,
                                 ChangeSignatureDialogBase dialog) {
    super(typeContext, defaultValueContext, dialog, ParameterInfoImpl.class);
    myProject = typeContext.getProject();
  }

  @Override
  protected ParameterInfoImpl createParameterInfo() {
    return new ParameterInfoImpl(-1);
  }

  @Override
  protected boolean isEllipsisType(int row) {
    return getTypeByRow(row) instanceof PsiEllipsisType;
  }

  @Override
  protected PsiCodeFragment createDefaultValueCodeFragment(String expressionText) {
    PsiExpressionCodeFragment codeFragment = JavaPsiFacade.getInstance(myProject).getElementFactory()
      .createExpressionCodeFragment(expressionText, myDefaultValueContext, null, true);
    codeFragment.setVisibilityChecker(JavaCodeFragment.VisibilityChecker.EVERYTHING_VISIBLE);
    return codeFragment;
  }

  @Override
  protected PsiCodeFragment createParameterTypeCodeFragment(String typeText) {
    return JavaPsiFacade.getInstance(myProject).getElementFactory().createTypeCodeFragment(typeText, myTypeContext, false, true, true);
  }

  @Nullable
  public PsiType getTypeByRow(int row) {
    Object typeValueAt = getValueAt(row, 0);
    LOG.assertTrue(typeValueAt instanceof PsiTypeCodeFragment);
    PsiType type;
    try {
      type = ((PsiTypeCodeFragment)typeValueAt).getType();
    }
    catch (PsiTypeCodeFragment.TypeSyntaxException e1) {
      type = null;
    }
    catch (PsiTypeCodeFragment.NoTypeException e1) {
      type = null;
    }
    return type;
  }

}
