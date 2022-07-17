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

import com.intellij.psi.*;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;

public class MakeMethodDelegate extends FixableUsageInfo {
    private final PsiMethod method;
    private final String delegate;

    public MakeMethodDelegate(PsiMethod method, String delegate) {
        super(method);
        this.method = method;
        this.delegate = delegate;
    }

    @Override
    public void fixUsage() throws IncorrectOperationException {
        final PsiCodeBlock body = method.getBody();
        assert body != null;
        final PsiStatement[] statements = body.getStatements();
        for(PsiStatement statement : statements){
            statement.delete();
        }
        @NonNls final StringBuilder delegation = new StringBuilder();
        final PsiType returnType = method.getReturnType();
        if(!PsiType.VOID.equals(returnType))
        {
           delegation.append("return ");
        }
        final String methodName = method.getName();
        delegation.append(delegate + '.' + methodName + '(');
        final PsiParameterList parameterList = method.getParameterList();
        final PsiParameter[] parameters = parameterList.getParameters();
        boolean first = true;
        for (PsiParameter parameter : parameters) {
            if(!first)
            {
                delegation.append(',');
            }
            first = false;
            final String parameterName = parameter.getName();
            delegation.append(parameterName);
        }
        delegation.append(");");
        final PsiManager manager = method.getManager();
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(manager.getProject());
        final String delegationText = delegation.toString();
        final PsiStatement delegationStatement =
                factory.createStatementFromText(delegationText, body);
        body.add(delegationStatement);
    }
}
