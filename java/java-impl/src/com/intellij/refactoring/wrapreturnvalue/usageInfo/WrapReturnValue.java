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
package com.intellij.refactoring.wrapreturnvalue.usageInfo;

import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.refactoring.psi.MutationUtils;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;

public class WrapReturnValue extends FixableUsageInfo {
    private final PsiReturnStatement statement;
    private final String type;

    public WrapReturnValue(PsiReturnStatement statement, String type) {
        super(statement);
        this.type = type;
        this.statement = statement;
    }

    public void fixUsage() throws IncorrectOperationException {
        final PsiExpression returnValue = statement.getReturnValue();
        assert returnValue != null;
        @NonNls final String newExpression =
                "new " + type + '(' + returnValue.getText() + ')';
        MutationUtils.replaceExpression(newExpression, returnValue);
    }
}
