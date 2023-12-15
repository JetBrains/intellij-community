/*
 * Copyright 2003-2019 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.migration;

import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Query;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class WhileCanBeForeachInspection extends BaseInspection {

  @Override
  public LocalQuickFix buildFix(Object... infos) {
    return new WhileCanBeForeachFix();
  }

  @Pattern(VALID_ID_PATTERN)
  @Override
  @NotNull
  public String getID() {
    return "WhileLoopReplaceableByForEach";
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("while.can.be.foreach.problem.descriptor");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public boolean shouldInspect(@NotNull PsiFile file) {
    return PsiUtil.isLanguageLevel5OrHigher(file);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new WhileCanBeForeachVisitor();
  }

  @Nullable
  static PsiStatement getPreviousStatement(PsiElement context) {
    final PsiElement prevStatement = PsiTreeUtil.skipWhitespacesAndCommentsBackward(context);
    if (!(prevStatement instanceof PsiStatement)) {
      return null;
    }
    return (PsiStatement)prevStatement;
  }

  private static class WhileCanBeForeachFix extends PsiUpdateModCommandQuickFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("foreach.replace.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement whileElement, @NotNull ModPsiUpdater updater) {
      final PsiWhileStatement whileStatement = (PsiWhileStatement)whileElement.getParent();
      final PsiStatement body = whileStatement.getBody();
      if (body == null) {
        return;
      }
      final PsiStatement initialization = getPreviousStatement(whileStatement);
      final PsiDeclarationStatement declaration = (PsiDeclarationStatement)initialization;
      if (declaration == null) {
        return;
      }
      final PsiElement declaredElement = declaration.getDeclaredElements()[0];
      if (!(declaredElement instanceof PsiLocalVariable iterator)) {
        return;
      }
      final PsiMethodCallExpression initializer = (PsiMethodCallExpression)PsiUtil.skipParenthesizedExprDown(iterator.getInitializer());
      if (initializer == null) {
        return;
      }
      final PsiReferenceExpression methodExpression = initializer.getMethodExpression();
      final PsiExpression collection = PsiUtil.skipParenthesizedExprDown(ExpressionUtils.getEffectiveQualifier(methodExpression));
      if (collection == null) {
        return;
      }
      final PsiType collectionType = collection.getType();
      if (collectionType == null) {
        return;
      }
      final PsiType contentType = ForCanBeForeachInspection.getContentType(collectionType, CommonClassNames.JAVA_LANG_ITERABLE);
      if (contentType == null) {
        return;
      }
      PsiType iteratorContentType = ForCanBeForeachInspection.getContentType(iterator.getType(), CommonClassNames.JAVA_UTIL_ITERATOR);
      if (TypeUtils.isJavaLangObject(iteratorContentType)) {
        iteratorContentType = ForCanBeForeachInspection.getContentType(initializer.getType(), CommonClassNames.JAVA_UTIL_ITERATOR);
      }
      if (iteratorContentType == null) {
        return;
      }
      final PsiStatement firstStatement = ForCanBeForeachInspection.getFirstStatement(body);
      final boolean isDeclaration = ForCanBeForeachInspection.isIteratorNextDeclaration(firstStatement, iterator, contentType);
      final PsiStatement statementToSkip;
      @NonNls final String contentVariableName;
      if (isDeclaration) {
        final PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)firstStatement;
        final PsiElement[] declaredElements = declarationStatement.getDeclaredElements();
        final PsiLocalVariable localVariable = (PsiLocalVariable)declaredElements[0];
        contentVariableName = localVariable.getName();
        iteratorContentType = localVariable.getType();
        statementToSkip = declarationStatement;
      }
      else {
        if (collection instanceof PsiReferenceExpression) {
          final PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)collection;
          final String collectionName = referenceElement.getReferenceName();
          contentVariableName = ForCanBeForeachInspection.createNewVariableName(whileStatement, iteratorContentType, collectionName);
        }
        else {
          contentVariableName = ForCanBeForeachInspection.createNewVariableName(whileStatement, iteratorContentType, null);
        }
        statementToSkip = null;
      }
      CommentTracker ct = new CommentTracker();
      @NonNls final StringBuilder newStatement = new StringBuilder();
      newStatement.append("for(");
      if (JavaCodeStyleSettings.getInstance(whileStatement.getContainingFile()).GENERATE_FINAL_PARAMETERS) {
        newStatement.append("final ");
      }
      final String canonicalText = iteratorContentType.getCanonicalText();
      newStatement.append(canonicalText).append(' ').append(contentVariableName).append(": ");
      if (!TypeConversionUtil.isAssignable(iteratorContentType, contentType)) {
        newStatement.append("(java.lang.Iterable<").append(canonicalText).append(">)");
      }
      newStatement.append(ct.text(collection)).append(')');

      ForCanBeForeachInspection.replaceIteratorNext(body, contentVariableName, iterator, contentType, statementToSkip, ct, newStatement);
      final Query<PsiReference> query = ReferencesSearch.search(iterator);
      boolean deleteIterator = true;
      for (PsiReference usage : query) {
        final PsiElement element = usage.getElement();
        if (PsiTreeUtil.isAncestor(whileStatement, element, true)) {
          continue;
        }
        final PsiAssignmentExpression assignment = PsiTreeUtil.getParentOfType(element, PsiAssignmentExpression.class);
        if (assignment == null) {
          // iterator is read after while loop,
          // so cannot be deleted
          deleteIterator = false;
          break;
        }
        final PsiExpression expression = assignment.getRExpression();
        final PsiTypeElement typeElement = iterator.getTypeElement();
        if (typeElement.isInferredType() &&
            (expression == null ||
             PsiTypes.nullType().equals(expression.getType()) ||
             expression instanceof PsiArrayInitializerExpression ||
             expression instanceof PsiFunctionalExpression) &&     
            PsiTypesUtil.replaceWithExplicitType(typeElement) == null) {
          deleteIterator = false;
          break;
        }
        iterator.setInitializer(expression);
        final PsiElement statement = assignment.getParent();
        final PsiElement lastChild = statement.getLastChild();
        if (lastChild instanceof PsiComment) {
          iterator.add(lastChild);
        }
        statement.replace(iterator);
        break;
      }
      if (deleteIterator) {
        new CommentTracker().deleteAndRestoreComments(iterator);
      }
      PsiElement result = ct.replaceAndRestoreComments(whileStatement, newStatement.toString());
      updater.moveCaretTo(result);
    }
  }

  private static class WhileCanBeForeachVisitor extends BaseInspectionVisitor {
    @Override
    public void visitWhileStatement(@NotNull PsiWhileStatement whileStatement) {
      super.visitWhileStatement(whileStatement);
      if (!isCollectionLoopStatement(whileStatement)) {
        return;
      }
      registerStatementError(whileStatement);
    }

    private static boolean isCollectionLoopStatement(PsiWhileStatement whileStatement) {
      final PsiStatement initialization = getPreviousStatement(whileStatement);
      final PsiVariable variable = ForCanBeForeachInspection.getIterableVariable(initialization, false);
      if (variable == null) {
        return false;
      }
      final PsiExpression condition = whileStatement.getCondition();
      if (!ForCanBeForeachInspection.isHasNext(condition, variable)) {
        return false;
      }
      if (!ForCanBeForeachInspection.hasSimpleNextCall(variable, whileStatement.getBody())) {
        return false;
      }
      PsiElement nextSibling = whileStatement.getNextSibling();
      while (nextSibling != null) {
        if (VariableAccessUtils.variableValueIsUsed(variable, nextSibling)) {
          return false;
        }
        nextSibling = nextSibling.getNextSibling();
      }
      return true;
    }
  }
}