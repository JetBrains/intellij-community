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

import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.refactoring.psi.MutationUtils;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.IncorrectOperationException;

public class ReplaceThisCallWithDelegateCall extends FixableUsageInfo {
    private final String delegateFieldName;
    private final PsiMethodCallExpression call;

     public ReplaceThisCallWithDelegateCall(PsiMethodCallExpression call, String delegateFieldName) {
        super(call);
        this.call = call;
        this.delegateFieldName = delegateFieldName;
    }

    @Override
    public void fixUsage() throws IncorrectOperationException {
        final PsiReferenceExpression methodExpression = call.getMethodExpression();
        final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
        if (qualifierExpression == null) {
            MutationUtils.replaceExpression(delegateFieldName + '.' + call.getText(), call);
        } else {
            MutationUtils.replaceExpression(delegateFieldName, qualifierExpression);
        }
    }
}
