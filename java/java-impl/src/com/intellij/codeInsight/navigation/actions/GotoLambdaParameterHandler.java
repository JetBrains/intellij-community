/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInsight.navigation.actions;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import org.jetbrains.annotations.Nullable;

public class GotoLambdaParameterHandler extends GotoDeclarationHandlerBase {
  @Override
  @Nullable
  public PsiElement getGotoDeclarationTarget(@Nullable PsiElement elementAt, Editor editor) {
    if (elementAt instanceof PsiIdentifier) {
      PsiElement parent = elementAt.getParent();
      if (parent instanceof PsiParameter && ((PsiParameter)parent).getTypeElement() == null) {
        final PsiElement gParent = parent.getParent();
        if (gParent instanceof PsiParameterList && gParent.getParent() instanceof PsiLambdaExpression) {
          return parent;
        }
      }
    }

    return null;
  }
}
