// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CallBuilder {
  private final PsiElementFactory factory;

  private String methodName = "extracted";
  private List<String> parameters = Collections.emptyList();

  private @Nullable String variableToAssign = null;
  private @Nullable PsiType variableType = null;

  private Computable<String> callStatementProducer = () -> null;

  public CallBuilder(@NotNull Project project) {
    this.factory = PsiElementFactory.getInstance(project);
  }

  public CallBuilder methodName(@NotNull String name) {
    this.methodName = name;
    return this;
  }

  public CallBuilder parameters(@NotNull List<String> parameters) {
    this.parameters = parameters;
    return this;
  }

  public List<String> parameters(){
    return parameters;
  }

  public CallBuilder assignToVariable(@NotNull String name) {
    this.variableToAssign = name;
    this.variableType = null;
    return this;
  }

  public CallBuilder declareVariable(PsiType type, String name) {
    this.variableType = type;
    this.variableToAssign = name;
    return this;
  }

  public CallBuilder guardMethodCall(@NotNull String exitStatement) {
    this.callStatementProducer = () -> "if (" + getCallExpression() + ")" + exitStatement;
    return this;
  }

  public CallBuilder returnNotNullVariable() {
    this.callStatementProducer = () -> "if (" + variableToAssign + " != null) return " + variableToAssign + ";";
    return this;
  }

  public CallBuilder guardNullVariable(String exitStatement) {
    this.callStatementProducer = () -> "if (" + variableToAssign + " == null) " + exitStatement;
    return this;
  }

  public CallBuilder returnMethodCall() {
    this.callStatementProducer = () -> "return " + getCallExpression() + ";";
    return this;
  }

  public CallBuilder methodCall(){
    this.callStatementProducer = () -> getCallExpression() + ";";
    return this;
  }

  private String getVariableDeclaration(String variableName, PsiType type) {
    if (variableName == null) return null;
    final String callExpression = getCallExpression();
    if (type != null) {
      final PsiExpression initializer = factory.createExpressionFromText(callExpression, null);
      return factory.createVariableDeclarationStatement(variableName, type, initializer).getText();
    }
    else {
      return variableToAssign + " = " + callExpression + ";";
    }
  }

  private String getCallExpression() {
    return methodName + "(" + StringUtil.join(parameters, ",") + ")";
  }

  private List<PsiStatement> parseStatementsFromText(String... statements) {
    final PsiCodeBlock container = factory.createCodeBlock();
    for (String statement : statements) {
      if (statement == null) continue;
      container.add(factory.createStatementFromText(statement, null));
    }
    return Arrays.asList(container.getStatements());
  }

  private String getCallStatement(Computable<String> callStatementProducer){
    if (variableToAssign == null && callStatementProducer == null) {
      return getCallExpression() + ";";
    } else {
      return callStatementProducer != null ? callStatementProducer.compute() : null;
    }
  }

  public List<PsiStatement> build() {
    return parseStatementsFromText(
      getVariableDeclaration(variableToAssign, variableType),
      getCallStatement(callStatementProducer)
    );
  }

}