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
package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionCodeFragment;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import org.jetbrains.annotations.NonNls;

public class PsiExpressionCodeFragmentImpl extends PsiCodeFragmentImpl implements PsiExpressionCodeFragment {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.PsiExpressionCodeFragmentImpl");
  private PsiType myExpectedType;

  public PsiExpressionCodeFragmentImpl(Project project,
                                       boolean isPhysical,
                                       @NonNls String name,
                                       CharSequence text,
                                       final PsiType expectedType) {
    super(project, JavaElementType.EXPRESSION_TEXT, isPhysical, name, text);
    setExpectedType(expectedType);
  }

  @Override
  public PsiExpression getExpression() {
    ASTNode exprChild = calcTreeElement().findChildByType(Constants.EXPRESSION_BIT_SET);
    if (exprChild == null) return null;
    return (PsiExpression)SourceTreeToPsiMap.treeElementToPsi(exprChild);
  }

  @Override
  public PsiType getExpectedType() {
    PsiType type = myExpectedType;
    if (type != null) {
      LOG.assertTrue(type.isValid());
    }
    return type;
  }

  @Override
  public void setExpectedType(PsiType type) {
    myExpectedType = type;
    if (type != null) {
      LOG.assertTrue(type.isValid());
    }
  }
}
