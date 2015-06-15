/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionCodeFragment;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiExpressionCodeFragmentImpl extends PsiCodeFragmentImpl implements PsiExpressionCodeFragment {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.PsiExpressionCodeFragmentImpl");
  private PsiType myExpectedType;

  public PsiExpressionCodeFragmentImpl(@NotNull Project project,
                                       boolean isPhysical,
                                       @NonNls String name,
                                       @NotNull CharSequence text,
                                       @Nullable final PsiType expectedType,
                                       @Nullable PsiElement context) {
    super(project, JavaElementType.EXPRESSION_TEXT, isPhysical, name, text, context);
    setExpectedType(expectedType);
  }

  @Nullable
  @Override
  public PsiExpression getExpression() {
    ASTNode exprChild = calcTreeElement().findChildByType(ElementType.EXPRESSION_BIT_SET);
    if (exprChild == null) return null;
    return (PsiExpression)SourceTreeToPsiMap.treeElementToPsi(exprChild);
  }

  @Override
  public PsiType getExpectedType() {
    PsiType type = myExpectedType;
    if (type != null && !type.isValid()) {
      return null;
    }
    return type;
  }

  @Override
  public void setExpectedType(@Nullable PsiType type) {
    myExpectedType = type;
    if (type != null) {
      LOG.assertTrue(type.isValid());
    }
  }
}
