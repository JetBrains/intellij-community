// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
        final @NonNls StringBuilder delegation = new StringBuilder();
        final PsiType returnType = method.getReturnType();
        if(!PsiTypes.voidType().equals(returnType))
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
