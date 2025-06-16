/*
 * Copyright 2003-2022 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.forloop;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTypesUtil;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.VariableNameGenerator;
import com.siyeh.ipp.base.MCIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ReplaceForEachLoopWithIndexedForLoopIntention extends MCIntention {

  @Override
  public @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("replace.for.each.loop.with.indexed.for.loop.intention.family.name");
  }

  @Override
  public @IntentionName @NotNull String getTextForElement(@NotNull PsiElement element) {
    return IntentionPowerPackBundle.message("replace.for.each.loop.with.indexed.for.loop.intention.name");
  }

  @Override
  public @NotNull PsiElementPredicate getElementPredicate() {
    return new IndexedForEachLoopPredicate();
  }

  @Override
  public void invoke(@NotNull PsiElement element) {
    final PsiForeachStatement statement = (PsiForeachStatement)element.getParent();
    if (statement == null) {
      return;
    }
    final PsiExpression iteratedValue = statement.getIteratedValue();
    if (iteratedValue == null) {
      return;
    }
    final PsiParameter iterationParameter = statement.getIterationParameter();
    final PsiType type = iterationParameter.getType();
    final PsiType iteratedValueType = iteratedValue.getType();
    if (iteratedValueType == null) {
      return;
    }
    final CommentTracker tracker = new CommentTracker();
    final boolean isArray = iteratedValueType instanceof PsiArrayType;
    final PsiElement parent = statement.getParent();
    PsiStatement context = (parent instanceof PsiLabeledStatement) ? (PsiStatement)parent : statement;
    final PsiElement reference = getReferenceToIterate(iteratedValue, context);

    final @NonNls StringBuilder newStatement = new StringBuilder();
    final String indexText = createVariableName("i", PsiTypes.intType(), statement);
    final String iteratedValueText = (reference instanceof PsiVariable) ? ((PsiVariable)reference).getName() : tracker.text(reference);
    createForLoopDeclaration(statement, isArray, iteratedValueText, indexText, newStatement);
    if (JavaCodeStyleSettings.getInstance(statement.getContainingFile()).GENERATE_FINAL_LOCALS) {
      newStatement.append("final ");
    }
    PsiTypeElement typeElement = iterationParameter.getTypeElement();
    newStatement.append(typeElement != null && typeElement.isInferredType() ? JavaKeywords.VAR : type.getCanonicalText());
    newStatement.append(' ');
    newStatement.append(iterationParameter.getName());
    newStatement.append('=');
    newStatement.append(iteratedValueText);
    if (isArray) {
      newStatement.append('[');
      newStatement.append(indexText);
      newStatement.append("];");
    }
    else {
      newStatement.append(".get(");
      newStatement.append(indexText);
      newStatement.append(");");
    }
    final PsiStatement body = statement.getBody();
    if (body == null) {
      return;
    }
    if (body instanceof PsiBlockStatement) {
      final PsiCodeBlock block = ((PsiBlockStatement)body).getCodeBlock();
      final PsiElement[] children = block.getChildren();
      for (int i = 1; i < children.length - 1; i++) {
        //skip the braces
        newStatement.append(tracker.text(children[i]));
      }
    }
    else {
      newStatement.append(tracker.text(body));
    }
    newStatement.append('}');
    if (reference instanceof PsiVariable) {
      if (!(context.getParent() instanceof PsiCodeBlock)) {
        context = BlockUtils.expandSingleStatementToBlockStatement(context);
      }
      final PsiElement newElement = context.getParent().addBefore(reference.getParent(), context);
      JavaCodeStyleManager.getInstance(context.getProject()).shortenClassReferences(newElement);
    }
    final PsiStatement elementToReplace = context instanceof PsiLabeledStatement ? ((PsiLabeledStatement)context).getStatement() : context;
    assert elementToReplace != null;
    PsiReplacementUtil.replaceStatementAndShortenClassNames(elementToReplace, newStatement.toString(), tracker);
  }

  protected void createForLoopDeclaration(PsiForeachStatement statement,
                                          boolean array,
                                          String iteratedValueText,
                                          String indexText,
                                          StringBuilder newStatement) {
    newStatement.append("for(int ");
    newStatement.append(indexText);
    newStatement.append("=0;");
    newStatement.append(indexText);
    newStatement.append('<');
    newStatement.append(iteratedValueText);
    newStatement.append(array ? ".length;" : ".size();");
    newStatement.append(indexText);
    newStatement.append("++){");
  }

  private static @Nullable String getVariableName(PsiExpression expression) {
    if (expression instanceof PsiMethodCallExpression methodCallExpression) {
      final PsiReferenceExpression methodExpression =
        methodCallExpression.getMethodExpression();
      final String name = methodExpression.getReferenceName();
      if (name == null) {
        return null;
      }
      if (name.startsWith("to") && name.length() > 2) {
        return StringUtil.decapitalize(name.substring(2));
      }
      else if (name.startsWith("get") && name.length() > 3) {
        return StringUtil.decapitalize(name.substring(3));
      }
      else {
        return name;
      }
    }
    else if (expression instanceof PsiTypeCastExpression castExpression) {
      final PsiExpression operand = castExpression.getOperand();
      return getVariableName(operand);
    }
    else if (expression instanceof PsiArrayAccessExpression arrayAccessExpression) {
      final PsiExpression arrayExpression =
        arrayAccessExpression.getArrayExpression();
      final String name = getVariableName(arrayExpression);
      return (name == null) ? null : StringUtil.unpluralize(name);
    }
    else if (expression instanceof PsiParenthesizedExpression parenthesizedExpression) {
      final PsiExpression innerExpression =
        parenthesizedExpression.getExpression();
      return getVariableName(innerExpression);
    }
    else if (expression instanceof PsiJavaCodeReferenceElement referenceElement) {
      final String referenceName = referenceElement.getReferenceName();
      if (referenceName == null) {
        return expression.getText();
      }
      return referenceName;
    }
    return null;
  }

  private static PsiElement getReferenceToIterate(PsiExpression expression, PsiElement context) {
    if (expression instanceof PsiMethodCallExpression ||
        expression instanceof PsiTypeCastExpression ||
        expression instanceof PsiArrayAccessExpression ||
        expression instanceof PsiNewExpression) {
      final String variableName = getVariableName(expression);
      return createVariable(variableName, expression, context);
    }
    else if (expression instanceof PsiParenthesizedExpression parenthesizedExpression) {
      final PsiExpression innerExpression =
        parenthesizedExpression.getExpression();
      return getReferenceToIterate(innerExpression, context);
    }
    else if (expression instanceof PsiJavaCodeReferenceElement referenceElement) {
      final String variableName = getVariableName(expression);
      if (referenceElement.isQualified()) {
        return createVariable(variableName, expression, context);
      }
      final PsiElement target = referenceElement.resolve();
      if (target instanceof PsiVariable) {
        // maybe should not do this for local variables outside of
        // anonymous classes
        return referenceElement;
      }
      return createVariable(variableName, expression, context);
    }
    return expression;
  }

  private static PsiVariable createVariable(String variableNameRoot, PsiExpression iteratedValue, PsiElement context) {
    final String variableName =
      createVariableName(variableNameRoot, iteratedValue);
    final Project project = context.getProject();
    PsiType iteratedValueType = iteratedValue.getType();
    assert iteratedValueType != null;
    iteratedValueType = PsiTypesUtil.removeExternalAnnotations(iteratedValueType);
    final PsiElementFactory elementFactory =
      JavaPsiFacade.getElementFactory(project);
    final PsiDeclarationStatement declarationStatement =
      elementFactory.createVariableDeclarationStatement(variableName,
                                                        iteratedValueType, iteratedValue);
    return (PsiVariable)declarationStatement.getDeclaredElements()[0];
  }

  public static String createVariableName(@Nullable String baseName, @NotNull PsiExpression assignedExpression) {
    return new VariableNameGenerator(assignedExpression, VariableKind.LOCAL_VARIABLE).byName(baseName).byExpression(assignedExpression)
      .byType(assignedExpression.getType()).generate(true);
  }

  public static String createVariableName(@Nullable String baseName,
                                          @NotNull PsiType type,
                                          @NotNull PsiElement context) {
    return new VariableNameGenerator(context, VariableKind.LOCAL_VARIABLE).byName(baseName).byType(type).generate(true);
  }
}