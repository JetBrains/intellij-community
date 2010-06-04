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
package com.intellij.refactoring.extractclass.usageInfo;

import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.refactoring.psi.MutationUtils;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.IncorrectOperationException;

public class ReplaceStaticVariableAccess extends FixableUsageInfo {
    private final PsiReferenceExpression expression;
    private final String delegateClass;
  private final boolean myEnumConstant;

  public ReplaceStaticVariableAccess(PsiReferenceExpression expression, String delegateClass, boolean enumConstant) {
        super(expression);
        this.expression = expression;
        this.delegateClass = delegateClass;
    myEnumConstant = enumConstant;
  }

    public void fixUsage() throws IncorrectOperationException {
      MutationUtils.replaceExpression(delegateClass + '.' + expression.getReferenceName() + (myEnumConstant ? "." + PropertyUtil.suggestGetterName("value", expression.getType())+ 
                                                                                                              "()" : ""), expression);
    }
}
