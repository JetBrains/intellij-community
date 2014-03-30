/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInsight.folding.impl;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import org.jetbrains.annotations.Nullable;

public class JavaFoldingBuilder extends JavaFoldingBuilderBase {

  @Override
  protected boolean isBelowRightMargin(Project project, int lineLength) {
    final CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(project);
    return lineLength <= settings.RIGHT_MARGIN;
  }

  @Override
  protected boolean shouldShowExplicitLambdaType(PsiAnonymousClass anonymousClass, PsiNewExpression expression) {
    PsiElement parent = expression.getParent();
    if (parent instanceof PsiReferenceExpression || parent instanceof PsiAssignmentExpression) {
      return true;
    }

    ExpectedTypeInfo[] types = ExpectedTypesProvider.getExpectedTypes(expression, false);
    return types.length != 1 || !types[0].getType().equals(anonymousClass.getBaseClassType());
  }

  @Nullable
  @Override
  public TextRange getRangeToFold(PsiElement element) {
    if (element instanceof SyntheticElement) {
      return null;
    }
    return super.getRangeToFold(element);    //To change body of overridden methods use File | Settings | File Templates.
  }
}

