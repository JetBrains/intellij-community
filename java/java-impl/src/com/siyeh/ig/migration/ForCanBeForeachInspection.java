// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.migration;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.UpdateInspectionOptionFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.*;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;
import static com.intellij.util.ObjectUtils.tryCast;
import static com.siyeh.ig.psiutils.ParenthesesUtils.getParentSkipParentheses;

public final class ForCanBeForeachInspection extends BaseInspection {
  @SuppressWarnings("PublicField")
  public boolean REPORT_INDEXED_LOOP = true;
  @SuppressWarnings("PublicField")
  public boolean ignoreUntypedCollections;

  @Override
  protected LocalQuickFix @NotNull [] buildFixes(Object... infos) {
    boolean indexed = (boolean)infos[0];
    if (indexed) {
      return new LocalQuickFix[]{
        new ForCanBeForeachFix(ignoreUntypedCollections),
        LocalQuickFix.from(new UpdateInspectionOptionFix(this, "REPORT_INDEXED_LOOP",
                                                         InspectionGadgetsBundle.message("for.can.be.foreach.fix.no.indexed"), false)),
      };
    }
    return new LocalQuickFix[]{new ForCanBeForeachFix(ignoreUntypedCollections)};
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("REPORT_INDEXED_LOOP", InspectionGadgetsBundle.message("for.can.be.foreach.option")),
      checkbox("ignoreUntypedCollections", InspectionGadgetsBundle.message("for.can.be.foreach.option2")));
  }

  static boolean isIndexedListLoopStatement(PsiForStatement forStatement, boolean ignoreUntypedCollections) {
    final PsiStatement initialization = forStatement.getInitialization();
    if (!(initialization instanceof PsiDeclarationStatement declaration)) {
      return false;
    }
    final PsiElement[] declaredElements = declaration.getDeclaredElements();
    final PsiElement secondDeclaredElement;
    if (declaredElements.length == 1) {
      secondDeclaredElement = null;
    }
    else if (declaredElements.length == 2) {
      secondDeclaredElement = declaredElements[1];
    }
    else {
      return false;
    }
    final PsiElement declaredElement = declaredElements[0];
    if (!(declaredElement instanceof PsiVariable indexVariable)) {
      return false;
    }
    final PsiExpression initialValue = indexVariable.getInitializer();
    final Object constant = ExpressionUtils.computeConstantExpression(initialValue);
    if (!(constant instanceof Number number)) {
      return false;
    }
    if (number.intValue() != 0) {
      return false;
    }
    final PsiExpression condition = forStatement.getCondition();
    final Holder collectionHolder = getCollectionFromSizeComparison(condition, indexVariable, secondDeclaredElement);
    if (collectionHolder == null) {
      return false;
    }
    final PsiStatement update = forStatement.getUpdate();
    if (!VariableAccessUtils.variableIsIncremented(indexVariable, update)) {
      return false;
    }
    final PsiStatement body = forStatement.getBody();
    if (!isIndexVariableOnlyUsedAsListIndex(collectionHolder, indexVariable, body)) {
      return false;
    }
    if (!collectionHolder.isDummyVariable()) {
      final PsiVariable collection = collectionHolder.getVariable();
      final PsiClassType collectionType = (PsiClassType)collection.getType();
      final PsiType[] parameters = collectionType.getParameters();
      if (ignoreUntypedCollections && parameters.length == 0) {
        return false;
      }
      return !VariableAccessUtils.variableIsAssigned(collection, body) &&
             !collectionMayBeChangedBeforeLoop(forStatement, collectionHolder);
    }
    return !collectionMayBeChangedBeforeLoop(forStatement, collectionHolder);
  }

  static boolean isArrayLoopStatement(PsiForStatement forStatement) {
    final PsiStatement initialization = forStatement.getInitialization();
    if (!(initialization instanceof PsiDeclarationStatement declaration)) {
      return false;
    }
    final PsiElement[] declaredElements = declaration.getDeclaredElements();
    final PsiElement secondDeclaredElement;
    if (declaredElements.length == 1) {
      secondDeclaredElement = null;
    }
    else if (declaredElements.length == 2) {
      secondDeclaredElement = declaredElements[1];
    }
    else {
      return false;
    }
    final PsiElement declaredElement = declaredElements[0];
    if (!(declaredElement instanceof PsiVariable indexVariable)) {
      return false;
    }
    final PsiExpression initialValue = indexVariable.getInitializer();
    final Object constant = ExpressionUtils.computeConstantExpression(initialValue);
    if (!(constant instanceof Integer integer)) {
      return false;
    }
    if (integer.intValue() != 0) {
      return false;
    }
    final PsiStatement update = forStatement.getUpdate();
    if (!VariableAccessUtils.variableIsIncremented(indexVariable, update)) {
      return false;
    }
    final PsiExpression condition = forStatement.getCondition();
    final PsiReferenceExpression arrayReference = getVariableReferenceFromCondition(condition, indexVariable, secondDeclaredElement);
    if (arrayReference == null) {
      return false;
    }
    if (!(arrayReference.getType() instanceof PsiArrayType)) {
      return false;
    }
    final PsiElement element = arrayReference.resolve();
    if (!(element instanceof PsiVariable arrayVariable)) {
      return false;
    }
    final PsiStatement body = forStatement.getBody();
    return body == null ||
           isIndexVariableOnlyUsedAsIndex(arrayVariable, indexVariable, body) &&
           !VariableAccessUtils.variableIsAssigned(arrayVariable, body) &&
           !VariableAccessUtils.arrayContentsAreAssigned(arrayVariable, body) &&
           !arrayMayBeChangedBeforeLoop(arrayVariable, arrayReference, forStatement);
  }

  private static boolean arrayMayBeChangedBeforeLoop(@NotNull PsiVariable arrayVariable,
                                                     @NotNull PsiReferenceExpression from,
                                                     @Nullable PsiStatement to) {
    if (to == null) return false;
    int fromOffset = from.getTextOffset();
    int toOffset = to.getTextOffset();
    if (fromOffset >= toOffset) return false;
    PsiElement context = PsiTreeUtil.findCommonParent(from, to);
    PsiStatement fromStatement = PsiTreeUtil.getParentOfType(from, PsiStatement.class);
    if(fromStatement == null) return false;
    if (context == null) return false;
    if (arrayVariable instanceof PsiLocalVariable || arrayVariable instanceof PsiParameter) {
      return processFromAssignmentToLoop(fromStatement, to, getIsAssignedPredicateWithSkipOptions(arrayVariable));
    }
    else {
      return processFromAssignmentToLoop(fromStatement, to, getIsAssignedPredicateWithSkipOptions(arrayVariable),
                                         getNonLocalSideEffectPredicateWithSkipOptions());
    }
  }


  private static Function<Predicate<PsiElement>, Predicate<PsiElement>> getIsAssignedPredicateWithSkipOptions(PsiVariable variable) {
    return predicate -> {
      VariableAssignedVisitor visitor = new VariableAssignedVisitor(variable, predicate, true);
      return (Predicate<PsiElement>)element -> {
        element.accept(visitor);
        return visitor.isAssigned();
      };
    };
  }

  private static Function<Predicate<PsiElement>, Predicate<PsiElement>> getNonLocalSideEffectPredicateWithSkipOptions() {
    return predicate -> (Predicate<PsiElement>)element -> SideEffectChecker.mayHaveNonLocalSideEffects(element, predicate);
  }

  private static Function<Predicate<PsiElement>, Predicate<PsiElement>> getSideEffectPredicateWithSkipOptions() {
    return predicate -> (Predicate<PsiElement>)element -> SideEffectChecker.mayHaveSideEffects(element, predicate);
  }

  private static boolean processFromAssignmentToLoop(@NotNull PsiStatement fromAssignment,
                                                     @NotNull PsiStatement untilLoop,
                                                     Function<Predicate<PsiElement>, Predicate<PsiElement>>... predicatesWithSkipOptions) {
    int fromOffset = fromAssignment.getTextOffset();
    int toOffset = untilLoop.getTextOffset();
    if (fromOffset >= toOffset) return false;
    PsiElement context = PsiTreeUtil.findCommonParent(fromAssignment, untilLoop);
    if (context == null) return false;
    if (fromAssignment.getParent() != context) return false;
    //small optimization
    if (untilLoop.getParent() == context) {
      PsiStatement nextSibling = PsiTreeUtil.getNextSiblingOfType(fromAssignment, PsiStatement.class);
      if (nextSibling == untilLoop) {
        return false;
      }
      PsiStatement nextNextSibling = PsiTreeUtil.getNextSiblingOfType(nextSibling, PsiStatement.class);
      if (nextNextSibling == untilLoop) {
        context = nextSibling;
      }
    }
    List<Predicate<PsiElement>> predicates = Arrays.stream(predicatesWithSkipOptions)
      .map(t -> t.apply(element -> !(element.getTextOffset() < toOffset && element.getTextOffset() + element.getTextLength() > fromOffset)))
      .toList();
    PsiElement finalContext = context;
    return ContainerUtil.exists(predicates, t -> t.test(finalContext));
  }

  private static boolean isIndexVariableOnlyUsedAsIndex(
    @NotNull PsiVariable arrayVariable,
    @NotNull PsiVariable indexVariable,
    @Nullable PsiStatement body) {
    if (body == null) {
      return true;
    }
    final VariableOnlyUsedAsIndexVisitor visitor =
      new VariableOnlyUsedAsIndexVisitor(arrayVariable, indexVariable);
    body.accept(visitor);
    return visitor.isIndexVariableUsedOnlyAsIndex();
  }

  private static boolean isIndexVariableOnlyUsedAsListIndex(
    Holder collectionHolder, PsiVariable indexVariable,
    PsiStatement body) {
    if (body == null) {
      return true;
    }
    final VariableOnlyUsedAsListIndexVisitor visitor = new VariableOnlyUsedAsListIndexVisitor(collectionHolder, indexVariable);
    body.accept(visitor);
    return visitor.isIndexVariableUsedOnlyAsIndex();
  }

  static boolean isCollectionLoopStatement(PsiForStatement forStatement, boolean ignoreUntypedCollections) {
    final PsiStatement initialization = forStatement.getInitialization();
    final PsiVariable variable = getIterableVariable(initialization, ignoreUntypedCollections);
    if (variable == null) {
      return false;
    }
    final PsiExpression condition = forStatement.getCondition();
    if (!isHasNext(condition, variable)) {
      return false;
    }
    final PsiStatement update = forStatement.getUpdate();
    if (update != null && !(update instanceof PsiEmptyStatement)) {
      return false;
    }
    final PsiStatement body = forStatement.getBody();
    if (body == null) {
      return false;
    }
    return hasSimpleNextCall(variable, body);
  }

  static PsiVariable getIterableVariable(PsiStatement statement, boolean ignoreUntypedCollections) {
    if (!(statement instanceof PsiDeclarationStatement declaration)) {
      return null;
    }
    final PsiElement[] declaredElements = declaration.getDeclaredElements();
    if (declaredElements.length != 1) {
      return null;
    }
    final PsiElement declaredElement = declaredElements[0];
    if (!(declaredElement instanceof PsiVariable variable)) {
      return null;
    }
    if (!TypeUtils.variableHasTypeOrSubtype(variable, CommonClassNames.JAVA_UTIL_ITERATOR, "java.util.ListIterator")) {
      return null;
    }
    final PsiExpression initialValue = PsiUtil.skipParenthesizedExprDown(variable.getInitializer());
    if (!(initialValue instanceof PsiMethodCallExpression initialCall)) {
      return null;
    }
    if (!initialCall.getArgumentList().isEmpty()) {
      return null;
    }
    final PsiReferenceExpression initialMethodExpression = initialCall.getMethodExpression();
    @NonNls final String initialCallName = initialMethodExpression.getReferenceName();
    if (!HardcodedMethodConstants.ITERATOR.equals(initialCallName) && !"listIterator".equals(initialCallName)) {
      return null;
    }
    final PsiExpression qualifier = ExpressionUtils.getEffectiveQualifier(initialMethodExpression);
    if (qualifier == null || qualifier instanceof PsiSuperExpression) {
      return null;
    }
    final PsiType qualifierType = qualifier.getType();
    if (!(qualifierType instanceof PsiClassType classType)) {
      return null;
    }
    final PsiClass qualifierClass = classType.resolve();
    if (ignoreUntypedCollections) {
      final PsiClassType type = (PsiClassType)variable.getType();
      final PsiType[] parameters = type.getParameters();
      final PsiType[] parameters1 = classType.getParameters();
      if (parameters.length == 0 && parameters1.length == 0) {
        return null;
      }
    }
    if (!InheritanceUtil.isInheritor(qualifierClass, CommonClassNames.JAVA_LANG_ITERABLE)) {
      return null;
    }
    return variable;
  }

  static boolean hasSimpleNextCall(PsiVariable iterator, PsiElement context) {
    if (context == null) return false;
    final IteratorNextVisitor visitor = new IteratorNextVisitor(iterator, context);
    context.accept(visitor);
    return visitor.hasSimpleNextCall();
  }

  static boolean isHasNext(PsiExpression condition, PsiVariable iterator) {
    condition = PsiUtil.skipParenthesizedExprDown(condition);
    if (!(condition instanceof PsiMethodCallExpression call)) {
      return false;
    }
    if (!call.getArgumentList().isEmpty()) {
      return false;
    }
    final PsiReferenceExpression methodExpression = call.getMethodExpression();
    final String methodName = methodExpression.getReferenceName();
    if (!HardcodedMethodConstants.HAS_NEXT.equals(methodName)) {
      return false;
    }
    final PsiExpression qualifier = methodExpression.getQualifierExpression();
    return ExpressionUtils.isReferenceTo(qualifier, iterator);
  }

  @Nullable
  private static PsiReferenceExpression getVariableReferenceFromCondition(PsiExpression condition,
                                                                          PsiVariable variable,
                                                                          PsiElement secondDeclaredElement) {
    final PsiBinaryExpression binaryExpression = tryCast(PsiUtil.skipParenthesizedExprDown(condition), PsiBinaryExpression.class);
    if (binaryExpression == null) return null;
    final IElementType tokenType = binaryExpression.getOperationTokenType();
    final PsiExpression lhs = PsiUtil.skipParenthesizedExprDown(binaryExpression.getLOperand());
    final PsiExpression rhs = PsiUtil.skipParenthesizedExprDown(binaryExpression.getROperand());
    if (lhs == null || rhs == null) return null;
    PsiReferenceExpression referenceExpression;
    if (tokenType.equals(JavaTokenType.LT)) {
      if (!ExpressionUtils.isReferenceTo(lhs, variable) || !(rhs instanceof PsiReferenceExpression)) {
        return null;
      }
      referenceExpression = (PsiReferenceExpression)rhs;
    }
    else if (tokenType.equals(JavaTokenType.GT)) {
      if (!ExpressionUtils.isReferenceTo(rhs, variable) || !(lhs instanceof PsiReferenceExpression)) {
        return null;
      }
      referenceExpression = (PsiReferenceExpression)lhs;
    }
    else {
      return null;
    }
    if (ExpressionUtils.getArrayFromLengthExpression(referenceExpression) == null) {
      final PsiElement target = referenceExpression.resolve();
      if (secondDeclaredElement != null && !secondDeclaredElement.equals(target)) return null;
      if (target instanceof PsiVariable maxVariable) {
        final PsiCodeBlock context = PsiTreeUtil.getParentOfType(maxVariable, PsiCodeBlock.class);
        if (context == null || VariableAccessUtils.variableIsAssigned(maxVariable, context)) return null;
        final PsiExpression expression = PsiUtil.skipParenthesizedExprDown(maxVariable.getInitializer());
        if (!(expression instanceof PsiReferenceExpression)) return null;
        referenceExpression = (PsiReferenceExpression)expression;
        if (ExpressionUtils.getArrayFromLengthExpression(referenceExpression) == null) {
          return null;
        }
      }
    }
    else {
      if (secondDeclaredElement != null) return null;
    }
    final PsiExpression qualifierExpression = PsiUtil.skipParenthesizedExprDown(referenceExpression.getQualifierExpression());
    if (qualifierExpression instanceof PsiReferenceExpression) {
      return (PsiReferenceExpression)qualifierExpression;
    }
    else if (qualifierExpression instanceof PsiThisExpression ||
             qualifierExpression instanceof PsiSuperExpression ||
             qualifierExpression == null) {
      return referenceExpression;
    }
    else {
      return null;
    }
  }

  @Nullable
  private static Holder getCollectionFromSizeComparison(PsiExpression condition, PsiVariable variable, PsiElement secondDeclaredElement) {
    condition = PsiUtil.skipParenthesizedExprDown(condition);
    if (!(condition instanceof PsiBinaryExpression binaryExpression)) {
      return null;
    }
    final IElementType tokenType = binaryExpression.getOperationTokenType();
    final PsiExpression rhs = binaryExpression.getROperand();
    final PsiExpression lhs = binaryExpression.getLOperand();
    if (tokenType.equals(JavaTokenType.LT)) {
      if (!ExpressionUtils.isReferenceTo(lhs, variable)) {
        return null;
      }
      return getCollectionFromListMethodCall(rhs, HardcodedMethodConstants.SIZE, secondDeclaredElement);
    }
    if (tokenType.equals(JavaTokenType.GT)) {
      if (!ExpressionUtils.isReferenceTo(rhs, variable)) {
        return null;
      }
      return getCollectionFromListMethodCall(lhs, HardcodedMethodConstants.SIZE, secondDeclaredElement);
    }
    return null;
  }

  static boolean expressionIsListGetLookup(PsiExpression expression) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (!(expression instanceof PsiMethodCallExpression reference)) {
      return false;
    }
    final PsiReferenceExpression methodExpression = reference.getMethodExpression();
    final PsiElement resolved = methodExpression.resolve();
    if (!(resolved instanceof PsiMethod method)) {
      return false;
    }
    if (!HardcodedMethodConstants.GET.equals(method.getName())) {
      return false;
    }
    final PsiClass aClass = method.getContainingClass();
    return InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_UTIL_LIST);
  }

  @Nullable
  private static Holder getCollectionFromListMethodCall(PsiExpression expression, String methodName, PsiElement secondDeclaredElement) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (expression instanceof PsiReferenceExpression) {
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)expression;
      final PsiElement target = referenceExpression.resolve();
      if (secondDeclaredElement != null && !secondDeclaredElement.equals(target)) {
        return null;
      }
      if (!(target instanceof PsiVariable variable)) {
        return null;
      }
      final PsiCodeBlock context = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
      if (context == null) {
        return null;
      }
      if (VariableAccessUtils.variableIsAssigned(variable, context)) {
        return null;
      }
      expression = PsiUtil.skipParenthesizedExprDown(variable.getInitializer());
    }
    else if (secondDeclaredElement != null) {
      return null;
    }
    if (!(expression instanceof PsiMethodCallExpression methodCallExpression)) {
      return null;
    }
    final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
    final String referenceName = methodExpression.getReferenceName();
    if (!methodName.equals(referenceName)) {
      return null;
    }
    final PsiMethod method = methodCallExpression.resolveMethod();
    if (method == null) {
      return null;
    }
    final PsiClass containingClass = method.getContainingClass();
    if (!InheritanceUtil.isInheritor(containingClass, CommonClassNames.JAVA_UTIL_LIST)) {
      return null;
    }
    final PsiExpression qualifierExpression = PsiUtil.skipParenthesizedExprDown(methodExpression.getQualifierExpression());
    if (qualifierExpression == null ||
        qualifierExpression instanceof PsiThisExpression ||
        qualifierExpression instanceof PsiSuperExpression) {
      return new Holder(methodExpression);
    }
    if (!(qualifierExpression instanceof PsiReferenceExpression)) {
      return null;
    }
    final PsiReferenceExpression referenceExpression =
      (PsiReferenceExpression)qualifierExpression;
    final PsiElement target = referenceExpression.resolve();
    if (!(target instanceof PsiVariable)) {
      return null;
    }
    final PsiVariable variable = (PsiVariable)target;
    return new Holder(variable, methodExpression);
  }

  private static boolean collectionMayBeChangedBeforeLoop(PsiForStatement forStatement,
                                                          Holder holder) {
    PsiStatement sizeStatement = PsiTreeUtil.getParentOfType(holder.getMethodExpression(), PsiStatement.class);
    if (forStatement == null || sizeStatement == null) return false;
    PsiElement context = PsiTreeUtil.findCommonParent(sizeStatement, forStatement);
    if (context == null) return false;
    if (sizeStatement.getParent() != context) return false;
    int toOffset = forStatement.getTextOffset();
    int fromOffset = sizeStatement.getTextOffset();
    if (toOffset <= fromOffset) return false;
    if (holder.isDummyVariable()) {
      return processFromAssignmentToLoop(sizeStatement, forStatement, getSideEffectPredicateWithSkipOptions());
    }
    else {
      return processFromAssignmentToLoop(sizeStatement, forStatement,
                                         getIsAssignedPredicateWithSkipOptions(holder.getVariable()),
                                         getNonLocalSideEffectPredicateWithSkipOptions());
    }
  }

  @Pattern(VALID_ID_PATTERN)
  @Override
  @NotNull
  public String getID() {
    return "ForLoopReplaceableByForEach";
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("for.can.be.foreach.problem.descriptor");
  }

  @Override
  public boolean shouldInspect(@NotNull PsiFile file) {
    return PsiUtil.isLanguageLevel5OrHigher(file);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ForCanBeForeachVisitor();
  }

  @Nullable
  static PsiType getContentType(PsiType type, String containerClassName) {
    final PsiType parameterType = PsiUtil.substituteTypeParameter(type, containerClassName, 0, true);
    return GenericsUtil.getVariableTypeByExpressionType(parameterType);
  }

  static void replaceIteratorNext(PsiElement element, String contentVariableName,
                                  PsiVariable iterator, PsiType contentType, PsiElement childToSkip,
                                  CommentTracker commentTracker, StringBuilder out) {
    if (isIteratorNext(element, iterator, contentType)) {
      if (!(element.getParent() instanceof PsiExpressionStatement)) {
        out.append(contentVariableName);
      }
    }
    else {
      final PsiElement[] children = element.getChildren();
      if (children.length == 0) {
        if (PsiUtil.isJavaToken(element, JavaTokenType.INSTANCEOF_KEYWORD) && out.charAt(out.length() - 1) != ' ') {
          out.append(' ');
        }
        out.append(commentTracker.text(element));
      }
      else {
        boolean skippingWhiteSpace = false;
        for (final PsiElement child : children) {
          if (shouldSkip(iterator, contentType, child) || child.equals(childToSkip)) {
            skippingWhiteSpace = true;
          }
          else if (!(child instanceof PsiWhiteSpace) || !skippingWhiteSpace) {
            skippingWhiteSpace = false;
            replaceIteratorNext(child, contentVariableName, iterator, contentType, childToSkip, commentTracker, out);
          }
        }
      }
    }
  }

  static boolean shouldSkip(PsiVariable iterator, PsiType contentType, PsiElement child) {
    if (!(child instanceof PsiExpressionStatement expressionStatement)) {
      return false;
    }
    final PsiExpression expression = expressionStatement.getExpression();
    return isIteratorNext(expression, iterator, contentType);
  }

  static boolean isIteratorNextDeclaration(
    PsiStatement statement, PsiVariable iterator,
    PsiType contentType) {
    if (!(statement instanceof PsiDeclarationStatement declarationStatement)) {
      return false;
    }
    final PsiElement[] declaredElements =
      declarationStatement.getDeclaredElements();
    if (declaredElements.length != 1) {
      return false;
    }
    final PsiElement declaredElement = declaredElements[0];
    if (!(declaredElement instanceof PsiVariable variable)) {
      return false;
    }
    final PsiExpression initializer = variable.getInitializer();
    return isIteratorNext(initializer, iterator, contentType);
  }

  static boolean isIteratorNext(PsiElement element, PsiVariable iterator, @NotNull PsiType contentType) {
    if (element == null) {
      return false;
    }
    if (element instanceof PsiParenthesizedExpression) {
      return isIteratorNext(((PsiParenthesizedExpression)element).getExpression(), iterator, contentType);
    }
    if (element instanceof PsiTypeCastExpression castExpression) {
      final PsiType type = castExpression.getType();
      if (!contentType.equals(type)) {
        return false;
      }
      final PsiExpression operand = castExpression.getOperand();
      return isIteratorNext(operand, iterator, contentType);
    }
    if (!(element instanceof PsiMethodCallExpression callExpression)) {
      return false;
    }
    if (!callExpression.getArgumentList().isEmpty()) {
      return false;
    }
    final PsiReferenceExpression reference = callExpression.getMethodExpression();
    @NonNls final String referenceName = reference.getReferenceName();
    if (!HardcodedMethodConstants.NEXT.equals(referenceName)) {
      return false;
    }

    final PsiExpression expression = reference.getQualifierExpression();
    return ExpressionUtils.isReferenceTo(expression, iterator);
  }

  static String createNewVariableName(@NotNull PsiElement scope, PsiType type, @Nullable String containerName) {
    return new VariableNameGenerator(scope, VariableKind.PARAMETER).byCollectionName(containerName).byType(type)
      .byName("value", "item", "element").generate(true);
  }

  @Nullable
  static PsiStatement getFirstStatement(PsiStatement body) {
    if (!(body instanceof PsiBlockStatement block)) {
      return body;
    }
    final PsiCodeBlock codeBlock = block.getCodeBlock();
    return ControlFlowUtils.getFirstStatementInBlock(codeBlock);
  }

  @NotNull
  private static String getVariableReferenceText(PsiReferenceExpression reference, PsiVariable variable, PsiElement context) {
    final String text = reference.getText();
    final PsiResolveHelper resolveHelper = PsiResolveHelper.getInstance(context.getProject());
    PsiExpression qualifier = reference.getQualifierExpression();
    while (qualifier != null) {
      if (!(qualifier instanceof PsiReferenceExpression)) return text;
      qualifier = ((PsiReferenceExpression)qualifier).getQualifierExpression();
    }
    final PsiVariable target = resolveHelper.resolveReferencedVariable(text, context);
    if (variable != target) {
      final PsiExpression effectiveQualifier = ExpressionUtils.getEffectiveQualifier(reference);
      if (effectiveQualifier != null) {
        return effectiveQualifier.getText() + "." + text;
      }
    }
    return text;
  }

  private static final class IteratorNextVisitor extends JavaRecursiveElementWalkingVisitor {

    private int numCallsToIteratorNext = 0;
    private boolean iteratorUsed = false;
    private final PsiVariable iterator;
    private final PsiElement context;

    private IteratorNextVisitor(PsiVariable iterator, PsiElement context) {
      this.iterator = iterator;
      this.context = context;
    }

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      if (iteratorUsed || numCallsToIteratorNext > 1) return;
      if (expression.getArgumentList().isEmpty()) {
        final PsiReferenceExpression methodExpression = expression.getMethodExpression();
        @NonNls final String methodName = methodExpression.getReferenceName();
        if (HardcodedMethodConstants.NEXT.equals(methodName)) {
          final PsiExpression qualifier = methodExpression.getQualifierExpression();
          if (ExpressionUtils.isReferenceTo(qualifier, iterator) && isExecutedExactlyOnce(expression)) {
            numCallsToIteratorNext++;
            return;
          }
        }
      }
      super.visitMethodCallExpression(expression);
    }

    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
      if (iteratorUsed || numCallsToIteratorNext > 1) return;
      super.visitReferenceExpression(expression);
      if (ExpressionUtils.isReferenceTo(expression, iterator)) {
        iteratorUsed = true;
        stopWalking();
      }
    }

    boolean hasSimpleNextCall() {
      return numCallsToIteratorNext == 1 && !iteratorUsed;
    }

    private boolean isExecutedExactlyOnce(@NotNull PsiElement element) {
      PsiElement parent = element.getParent();
      while (parent != null && parent != context) {
        if (parent instanceof PsiForStatement) {
          if (element != ((PsiForStatement)parent).getInitialization()) return false;
        }
        else if (parent instanceof PsiLoopStatement) return false;
        else if (parent instanceof PsiTryStatement) {
          if (element != ((PsiTryStatement)parent).getFinallyBlock()) return false;
        }
        else if (parent instanceof PsiIfStatement) {
          if (element != ((PsiIfStatement)parent).getCondition()) return false;
        }
        else if (parent instanceof PsiConditionalExpression) {
          if (element != ((PsiConditionalExpression)parent).getCondition()) return false;
        }
        else if (parent instanceof PsiSwitchBlock) {
          if (element != ((PsiSwitchBlock)parent).getExpression()) return false;
        }
        else if (parent instanceof PsiPolyadicExpression polyadicExpression) {
          IElementType tokenType = polyadicExpression.getOperationTokenType();
          if (polyadicExpression.getOperands()[0] != element &&
              (JavaTokenType.ANDAND == tokenType || JavaTokenType.OROR == tokenType)) return false;
        }
        else if (parent instanceof PsiLambdaExpression) return false;
        else if (parent instanceof PsiClass) return false;
        element = parent;
        parent = element.getParent();
      }
      return true;
    }
  }

  private static final class VariableOnlyUsedAsIndexVisitor extends JavaRecursiveElementWalkingVisitor {

    private boolean indexVariableUsedOnlyAsIndex = true;
    private boolean arrayAccessed = false;
    private final PsiVariable arrayVariable;
    private final PsiVariable indexVariable;

    private VariableOnlyUsedAsIndexVisitor(PsiVariable arrayVariable, PsiVariable indexVariable) {
      this.arrayVariable = arrayVariable;
      this.indexVariable = indexVariable;
    }

    @Override
    public void visitElement(@NotNull PsiElement element) {
      if (indexVariableUsedOnlyAsIndex) {
        super.visitElement(element);
      }
    }

    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression reference) {
      if (!indexVariableUsedOnlyAsIndex) {
        return;
      }
      super.visitReferenceExpression(reference);
      final PsiElement element = reference.resolve();
      if (!indexVariable.equals(element)) {
        return;
      }
      final PsiElement parent = getParentSkipParentheses(reference);
      if (!(parent instanceof PsiArrayAccessExpression arrayAccessExpression)) {
        indexVariableUsedOnlyAsIndex = false;
        return;
      }
      final PsiExpression arrayExpression = PsiUtil.skipParenthesizedExprDown(arrayAccessExpression.getArrayExpression());
      if (!(arrayExpression instanceof PsiReferenceExpression referenceExpression)) {
        indexVariableUsedOnlyAsIndex = false;
        return;
      }
      final PsiExpression qualifier = referenceExpression.getQualifierExpression();
      if (qualifier != null && !(qualifier instanceof PsiThisExpression) && !(qualifier instanceof PsiSuperExpression)) {
        indexVariableUsedOnlyAsIndex = false;
        return;
      }
      final PsiElement target = referenceExpression.resolve();
      if (!arrayVariable.equals(target)) {
        indexVariableUsedOnlyAsIndex = false;
        return;
      }
      arrayAccessed = true;
      final PsiElement arrayExpressionContext = getParentSkipParentheses(arrayAccessExpression);
      if (arrayExpressionContext instanceof PsiAssignmentExpression assignment) {
        final PsiExpression lhs = assignment.getLExpression();
        if (lhs.equals(arrayAccessExpression)) {
          indexVariableUsedOnlyAsIndex = false;
        }
      }
    }

    private boolean isIndexVariableUsedOnlyAsIndex() {
      return indexVariableUsedOnlyAsIndex && arrayAccessed;
    }
  }

  private static final class VariableOnlyUsedAsListIndexVisitor
    extends JavaRecursiveElementWalkingVisitor {

    private boolean indexVariableUsedOnlyAsIndex = true;
    private boolean listGetCalled;
    private final PsiVariable indexVariable;
    private final Holder collection;

    private VariableOnlyUsedAsListIndexVisitor(@NotNull Holder collection, @NotNull PsiVariable indexVariable) {
      this.collection = collection;
      this.indexVariable = indexVariable;
    }

    @Override
    public void visitElement(@NotNull PsiElement element) {
      if (indexVariableUsedOnlyAsIndex) {
        super.visitElement(element);
      }
    }

    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression reference) {
      if (!indexVariableUsedOnlyAsIndex) {
        return;
      }
      super.visitReferenceExpression(reference);
      final PsiElement element = reference.resolve();
      if (indexVariable.equals(element)) {
        if (!isListIndexExpression(reference)) {
          indexVariableUsedOnlyAsIndex = false;
        }
        else {
          listGetCalled = true;
        }
      }
      else if (collection.isDummyVariable()) {
        if (isListNonGetMethodCall(reference)) {
          indexVariableUsedOnlyAsIndex = false;
        }
      }
      else if (collection.getVariable().equals(element) && !isListReferenceInIndexExpression(reference)) {
        indexVariableUsedOnlyAsIndex = false;
      }
    }

    private boolean isIndexVariableUsedOnlyAsIndex() {
      return indexVariableUsedOnlyAsIndex && listGetCalled;
    }

    private boolean isListNonGetMethodCall(PsiReferenceExpression reference) {
      final PsiElement parent = reference.getParent();
      if (!(parent instanceof PsiMethodCallExpression methodCallExpression)) {
        return false;
      }
      final PsiMethod method =
        methodCallExpression.resolveMethod();
      if (method == null) {
        return false;
      }
      final PsiClass parentClass = PsiTreeUtil.getParentOfType(
        methodCallExpression, PsiClass.class);
      final PsiClass containingClass = method.getContainingClass();
      if (!InheritanceUtil.isInheritorOrSelf(parentClass,
                                             containingClass, true)) {
        return false;
      }
      return !isListGetExpression(methodCallExpression);
    }

    private boolean isListIndexExpression(PsiReferenceExpression reference) {
      final PsiElement referenceParent = getParentSkipParentheses(reference);
      if (!(referenceParent instanceof PsiExpressionList expressionList)) {
        return false;
      }
      final PsiElement parent = expressionList.getParent();
      if (!(parent instanceof PsiMethodCallExpression methodCallExpression)) {
        return false;
      }
      return isListGetExpression(methodCallExpression);
    }

    private boolean isListReferenceInIndexExpression(PsiReferenceExpression reference) {
      final PsiElement parent = getParentSkipParentheses(reference);
      if (!(parent instanceof PsiReferenceExpression)) {
        return false;
      }
      final PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiMethodCallExpression methodCallExpression)) {
        return false;
      }
      final PsiElement greatGrandParent = methodCallExpression.getParent();
      if (greatGrandParent instanceof PsiExpressionStatement) {
        return false;
      }
      return isListGetExpression(methodCallExpression);
    }

    private boolean isListGetExpression(PsiMethodCallExpression methodCallExpression) {
      if (methodCallExpression == null) {
        return false;
      }
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      final PsiExpression qualifierExpression = PsiUtil.skipParenthesizedExprDown(methodExpression.getQualifierExpression());
      if (!(qualifierExpression instanceof PsiReferenceExpression reference)) {
        if (collection.isDummyVariable() &&
            (qualifierExpression == null ||
             qualifierExpression instanceof PsiThisExpression ||
             qualifierExpression instanceof PsiSuperExpression)) {
          return expressionIsListGetLookup(methodCallExpression);
        }
        return false;
      }
      final PsiExpression qualifier = PsiUtil.skipParenthesizedExprDown(reference.getQualifierExpression());
      if (qualifier != null && !(qualifier instanceof PsiThisExpression) && !(qualifier instanceof PsiSuperExpression)) {
        return false;
      }
      final PsiElement target = reference.resolve();
      if (collection.isDummyVariable() || !collection.getVariable().equals(target)) {
        return false;
      }
      return expressionIsListGetLookup(methodCallExpression);
    }
  }

  private static class Holder {


    private final PsiVariable variable;
    private final PsiReferenceExpression methodExpression;

    Holder(@NotNull PsiVariable variable, @NotNull PsiReferenceExpression methodExpression) {
      this.variable = variable;
      this.methodExpression = methodExpression;
    }

    Holder(@NotNull PsiReferenceExpression methodExpression) {
      variable = null;
      this.methodExpression = methodExpression;
    }

    public PsiVariable getVariable() {
      return variable;
    }

    public PsiReferenceExpression getMethodExpression() {
      return methodExpression;
    }

    public boolean isDummyVariable() {
      return variable == null;
    }
  }

  private class ForCanBeForeachVisitor extends BaseInspectionVisitor {

    @Override
    public void visitForStatement(@NotNull PsiForStatement forStatement) {
      super.visitForStatement(forStatement);
      if (isArrayLoopStatement(forStatement) || isCollectionLoopStatement(forStatement, ignoreUntypedCollections)) {
        registerStatementError(forStatement, false);
      }
      else if (REPORT_INDEXED_LOOP && isIndexedListLoopStatement(forStatement, ignoreUntypedCollections)) {
        registerStatementError(forStatement, true);
      }
    }
  }

  private static class ForCanBeForeachFix extends PsiUpdateModCommandQuickFix {
    private final boolean myIgnoreUntypedCollections;

    ForCanBeForeachFix(boolean ignoreUntypedCollections) {
      this.myIgnoreUntypedCollections = ignoreUntypedCollections;
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("foreach.replace.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement forElement, @NotNull ModPsiUpdater updater) {
      final PsiElement parent = forElement.getParent();
      if (!(parent instanceof PsiForStatement forStatement)) {
        return;
      }
      if (isArrayLoopStatement(forStatement)) {
        replaceArrayLoopWithForeach(forStatement);
      }
      else if (isCollectionLoopStatement(forStatement, myIgnoreUntypedCollections)) {
        replaceCollectionLoopWithForeach(forStatement);
      }
      else if (isIndexedListLoopStatement(forStatement, myIgnoreUntypedCollections)) {
        replaceIndexedListLoopWithForeach(forStatement);
      }
    }

    private static void replaceIndexedListLoopWithForeach(@NotNull PsiForStatement forStatement) {
      final PsiVariable indexVariable = getIndexVariable(forStatement);
      if (indexVariable == null) {
        return;
      }
      PsiExpression collectionSize = getMaximum(forStatement);
      if (collectionSize instanceof PsiReferenceExpression referenceExpression) {
        final PsiElement target = referenceExpression.resolve();
        if (target instanceof PsiVariable variable) {
          collectionSize = PsiUtil.skipParenthesizedExprDown(variable.getInitializer());
        }
      }
      if (!(collectionSize instanceof PsiMethodCallExpression)) {
        return;
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)PsiUtil.skipParenthesizedExprDown(collectionSize);
      if (methodCallExpression == null) {
        return;
      }
      final PsiReferenceExpression listLengthExpression = methodCallExpression.getMethodExpression();
      final PsiExpression qualifier = PsiUtil.skipParenthesizedExprDown(ExpressionUtils.getEffectiveQualifier(listLengthExpression));
      if (qualifier == null) {
        return;
      }
      final PsiReferenceExpression listReference;
      if (qualifier instanceof PsiReferenceExpression) {
        listReference = (PsiReferenceExpression)qualifier;
      }
      else {
        listReference = null;
      }
      final PsiType type = qualifier.getType();
      if (type == null) {
        return;
      }
      PsiType parameterType = getContentType(type, CommonClassNames.JAVA_UTIL_COLLECTION);
      if (parameterType == null) {
        parameterType = TypeUtils.getObjectType(forStatement);
      }
      final PsiVariable listVariable;
      if (listReference == null) {
        listVariable = null;
      }
      else {
        final PsiElement target = listReference.resolve();
        if (!(target instanceof PsiVariable)) {
          return;
        }
        listVariable = (PsiVariable)target;
      }
      final PsiStatement body = forStatement.getBody();
      final PsiStatement firstStatement = getFirstStatement(body);
      final boolean isDeclaration = isListElementDeclaration(firstStatement, listVariable, indexVariable);
      final String contentVariableName;
      @NonNls final String finalString;
      final PsiStatement statementToSkip;
      if (isDeclaration) {
        final PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)firstStatement;
        final PsiElement[] declaredElements = declarationStatement.getDeclaredElements();
        final PsiElement declaredElement = declaredElements[0];
        if (!(declaredElement instanceof PsiVariable variable)) {
          return;
        }
        contentVariableName = variable.getName();
        parameterType = variable.getType();
        statementToSkip = declarationStatement;
        if (variable.hasModifierProperty(PsiModifier.FINAL)) {
          finalString = "final ";
        }
        else {
          finalString = "";
        }
      }
      else {
        final String collectionName;
        if (listReference == null) {
          collectionName = null;
        }
        else {
          collectionName = listReference.getReferenceName();
        }
        contentVariableName = createNewVariableName(forStatement, parameterType, collectionName);
        finalString = "";
        statementToSkip = null;
      }
      final CommentTracker ct = new CommentTracker();
      @NonNls final StringBuilder newStatement = new StringBuilder("for(");
      newStatement.append(finalString).append(parameterType.getCanonicalText()).append(' ').append(contentVariableName).append(": ");
      @NonNls final String listName;
      if (listReference == null) {
        listName = ct.text(qualifier);
      }
      else {
        listName = getVariableReferenceText(listReference, listVariable, forStatement);
      }
      newStatement.append(listName).append(')');
      if (body != null) {
        replaceCollectionGetAccess(body, contentVariableName, listVariable, indexVariable, statementToSkip, ct, newStatement);
      }
      ct.replaceAndRestoreComments(forStatement, newStatement.toString());
    }

    private static PsiExpression getMaximum(PsiForStatement statement) {
      final PsiExpression condition = PsiUtil.skipParenthesizedExprDown(statement.getCondition());
      if (!(condition instanceof PsiBinaryExpression binaryExpression)) {
        return null;
      }
      final PsiExpression lhs = PsiUtil.skipParenthesizedExprDown(binaryExpression.getLOperand());
      if (lhs == null) {
        return null;
      }
      final PsiExpression rhs = PsiUtil.skipParenthesizedExprDown(binaryExpression.getROperand());
      if (rhs == null) {
        return null;
      }
      final IElementType tokenType = binaryExpression.getOperationTokenType();
      if (JavaTokenType.LT.equals(tokenType)) {
        return rhs;
      }
      else if (JavaTokenType.GT.equals(tokenType)) {
        return lhs;
      }
      else {
        return null;
      }
    }

    @Nullable
    private static PsiVariable getIndexVariable(@NotNull PsiForStatement forStatement) {
      final PsiStatement initialization = forStatement.getInitialization();
      if (!(initialization instanceof PsiDeclarationStatement)) {
        return null;
      }
      final PsiElement declaration = ((PsiDeclarationStatement)initialization).getDeclaredElements()[0];
      if (!(declaration instanceof PsiVariable)) {
        return null;
      }
      return (PsiVariable)declaration;
    }

    private static void replaceCollectionLoopWithForeach(@NotNull PsiForStatement forStatement) {
      final PsiStatement body = forStatement.getBody();
      final PsiStatement firstStatement = getFirstStatement(body);
      final PsiStatement initialization = forStatement.getInitialization();
      if (!(initialization instanceof PsiDeclarationStatement declaration)) {
        return;
      }
      final PsiElement declaredIterator = declaration.getDeclaredElements()[0];
      if (!(declaredIterator instanceof PsiVariable iterator)) {
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
      final PsiType contentType = getContentType(collectionType, CommonClassNames.JAVA_LANG_ITERABLE);
      if (contentType == null) {
        return;
      }
      PsiType iteratorContentType = getContentType(iterator.getType(), CommonClassNames.JAVA_UTIL_ITERATOR);
      if (TypeUtils.isJavaLangObject(iteratorContentType)) {
        iteratorContentType = getContentType(initializer.getType(), CommonClassNames.JAVA_UTIL_ITERATOR);
      }
      if (iteratorContentType == null) {
        return;
      }

      final boolean isDeclaration = isIteratorNextDeclaration(firstStatement, iterator, contentType);
      final PsiStatement statementToSkip;
      @NonNls final String finalString;
      final String contentVariableName;
      if (isDeclaration) {
        final PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)firstStatement;
        final PsiElement[] declaredElements = declarationStatement.getDeclaredElements();
        final PsiElement declaredElement = declaredElements[0];
        if (!(declaredElement instanceof PsiVariable variable)) {
          return;
        }
        iteratorContentType = variable.getType();
        contentVariableName = variable.getName();
        statementToSkip = declarationStatement;
        finalString = variable.hasModifierProperty(PsiModifier.FINAL) ? "final " : "";
      }
      else {
        if (collection instanceof PsiReferenceExpression referenceExpression) {
          final String collectionName = referenceExpression.getReferenceName();
          contentVariableName = createNewVariableName(forStatement, contentType, collectionName);
        }
        else {
          contentVariableName = createNewVariableName(forStatement, contentType, null);
        }
        finalString = JavaCodeStyleSettings.getInstance(forStatement.getContainingFile()).GENERATE_FINAL_LOCALS ? "final " : "";
        statementToSkip = null;
      }
      final String contentTypeString = iteratorContentType.getCanonicalText();
      final CommentTracker ct = new CommentTracker();
      @NonNls final StringBuilder newStatement = new StringBuilder();
      newStatement.append("for(");
      newStatement.append(finalString);
      newStatement.append(contentTypeString);
      newStatement.append(' ');
      newStatement.append(contentVariableName);
      newStatement.append(": ");
      if (!TypeConversionUtil.isAssignable(iteratorContentType, contentType)) {
        newStatement.append('(').append("java.lang.Iterable<").append(contentTypeString).append('>').append(')');
      }
      newStatement.append(ct.text(collection));
      newStatement.append(')');
      replaceIteratorNext(body, contentVariableName, iterator, contentType, statementToSkip, ct, newStatement);
      ct.replaceAndRestoreComments(forStatement, newStatement.toString());
    }

    private static void replaceArrayLoopWithForeach(@NotNull PsiForStatement forStatement) {
      final PsiExpression maximum = getMaximum(forStatement);
      if (!(maximum instanceof PsiReferenceExpression arrayLengthExpression)) {
        return;
      }
      final PsiVariable indexVariable = getIndexVariable(forStatement);
      if (indexVariable == null) {
        return;
      }
      PsiReferenceExpression arrayReference = (PsiReferenceExpression)PsiUtil
        .skipParenthesizedExprDown(arrayLengthExpression.getQualifierExpression());
      if (arrayReference == null) {
        final PsiElement target = arrayLengthExpression.resolve();
        if (!(target instanceof PsiVariable variable)) {
          return;
        }
        final PsiExpression initializer = PsiUtil.skipParenthesizedExprDown(variable.getInitializer());
        if (!(initializer instanceof PsiReferenceExpression referenceExpression)) {
          return;
        }
        arrayReference = (PsiReferenceExpression)PsiUtil.skipParenthesizedExprDown(referenceExpression.getQualifierExpression());
        if (arrayReference == null) {
          return;
        }
      }
      final PsiType type = arrayReference.getType();
      if (!(type instanceof PsiArrayType arrayType)) {
        return;
      }
      PsiType componentType = arrayType.getComponentType();
      final PsiElement target = arrayReference.resolve();
      if (!(target instanceof PsiVariable arrayVariable)) {
        return;
      }
      final PsiStatement body = forStatement.getBody();
      final PsiStatement firstStatement = getFirstStatement(body);
      final boolean isDeclaration = isArrayElementDeclaration(firstStatement, arrayVariable, indexVariable);
      final String contentVariableName;
      @NonNls final String finalString;
      final PsiStatement statementToSkip;
      if (isDeclaration) {
        final PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)firstStatement;
        final PsiElement[] declaredElements = declarationStatement.getDeclaredElements();
        final PsiElement declaredElement = declaredElements[0];
        if (!(declaredElement instanceof PsiVariable variable)) {
          return;
        }
        componentType = variable.getType();
        if (VariableAccessUtils.variableIsAssigned(variable, forStatement)) {
          final String collectionName = arrayReference.getReferenceName();
          contentVariableName = createNewVariableName(forStatement, componentType, collectionName);
          if (JavaCodeStyleSettings.getInstance(forStatement.getContainingFile()).GENERATE_FINAL_LOCALS) {
            finalString = "final ";
          }
          else {
            finalString = "";
          }
          statementToSkip = null;
        }
        else {
          contentVariableName = variable.getName();
          statementToSkip = declarationStatement;
          if (variable.hasModifierProperty(PsiModifier.FINAL)) {
            finalString = "final ";
          }
          else {
            finalString = "";
          }
        }
      }
      else {
        final String collectionName = arrayReference.getReferenceName();
        contentVariableName = createNewVariableName(forStatement, componentType, collectionName);
        if (JavaCodeStyleSettings.getInstance(forStatement.getContainingFile()).GENERATE_FINAL_LOCALS) {
          finalString = "final ";
        }
        else {
          finalString = "";
        }
        statementToSkip = null;
      }
      final CommentTracker ct = new CommentTracker();
      @NonNls final StringBuilder newStatement = new StringBuilder();
      newStatement.append("for(");
      newStatement.append(finalString);
      newStatement.append(componentType.getCanonicalText());
      newStatement.append(' ');
      newStatement.append(contentVariableName);
      newStatement.append(": ");
      final String arrayName = getVariableReferenceText(arrayReference, arrayVariable, forStatement);
      newStatement.append(arrayName);
      newStatement.append(')');
      if (body != null) {
        replaceArrayAccess(body, contentVariableName, arrayVariable, indexVariable, statementToSkip, ct, newStatement);
      }
      ct.replaceAndRestoreComments(forStatement, newStatement.toString());
    }

    private static void replaceArrayAccess(
      PsiElement element, String contentVariableName,
      PsiVariable arrayVariable, PsiVariable indexVariable,
      PsiElement childToSkip, CommentTracker commentTracker, StringBuilder out) {
      if (element instanceof PsiExpression && isArrayLookup((PsiExpression)element, indexVariable, arrayVariable)) {
        out.append(contentVariableName);
      }
      else {
        final PsiElement[] children = element.getChildren();
        if (children.length == 0) {
          if (PsiUtil.isJavaToken(element, JavaTokenType.INSTANCEOF_KEYWORD) && out.charAt(out.length() - 1) != ' ') {
            out.append(' ');
          }
          out.append(commentTracker.text(element));
        }
        else {
          boolean skippingWhiteSpace = false;
          for (final PsiElement child : children) {
            if (child.equals(childToSkip)) {
              skippingWhiteSpace = true;
            }
            else if (child instanceof PsiWhiteSpace && skippingWhiteSpace) {
              //don't do anything
            }
            else {
              skippingWhiteSpace = false;
              replaceArrayAccess(child, contentVariableName, arrayVariable, indexVariable, childToSkip, commentTracker, out);
            }
          }
        }
      }
    }

    private static void replaceCollectionGetAccess(
      PsiElement element, String contentVariableName,
      PsiVariable listVariable, PsiVariable indexVariable,
      PsiElement childToSkip, CommentTracker commentTracker,
      StringBuilder out) {
      if (isListGetLookup(element, indexVariable, listVariable)) {
        out.append(contentVariableName);
      }
      else {
        final PsiElement[] children = element.getChildren();
        if (children.length == 0) {
          if (PsiUtil.isJavaToken(element, JavaTokenType.INSTANCEOF_KEYWORD) && out.charAt(out.length() - 1) != ' ') {
            out.append(' ');
          }
          out.append(commentTracker.text(element));
        }
        else {
          boolean skippingWhiteSpace = false;
          for (final PsiElement child : children) {
            if (child.equals(childToSkip)) {
              skippingWhiteSpace = true;
            }
            else if (child instanceof PsiWhiteSpace && skippingWhiteSpace) {
              //don't do anything
            }
            else {
              skippingWhiteSpace = false;
              replaceCollectionGetAccess(child, contentVariableName, listVariable, indexVariable, childToSkip, commentTracker, out);
            }
          }
        }
      }
    }

    private static boolean isListGetLookup(PsiElement element, PsiVariable indexVariable, PsiVariable listVariable) {
      if (!(element instanceof PsiExpression expression)) {
        return false;
      }
      if (!expressionIsListGetLookup(expression)) {
        return false;
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)PsiUtil.skipParenthesizedExprDown(expression);
      if (methodCallExpression == null) {
        return false;
      }
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      final PsiExpression qualifierExpression = PsiUtil.skipParenthesizedExprDown(methodExpression.getQualifierExpression());

      final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      final PsiExpression[] expressions = argumentList.getExpressions();
      if (expressions.length != 1) {
        return false;
      }
      if (!ExpressionUtils.isReferenceTo(expressions[0], indexVariable)) {
        return false;
      }
      if (qualifierExpression == null ||
          qualifierExpression instanceof PsiThisExpression ||
          qualifierExpression instanceof PsiSuperExpression) {
        return listVariable == null;
      }
      if (!(qualifierExpression instanceof PsiReferenceExpression referenceExpression)) {
        return false;
      }
      final PsiExpression qualifier = PsiUtil.skipParenthesizedExprDown(referenceExpression.getQualifierExpression());
      if (qualifier != null && !(qualifier instanceof PsiThisExpression) && !(qualifier instanceof PsiSuperExpression)) {
        return false;
      }
      final PsiElement target = referenceExpression.resolve();
      return listVariable.equals(target);
    }

    private static boolean isArrayElementDeclaration(PsiStatement statement, PsiVariable arrayVariable, PsiVariable indexVariable) {
      if (!(statement instanceof PsiDeclarationStatement declarationStatement)) {
        return false;
      }
      final PsiElement[] declaredElements = declarationStatement.getDeclaredElements();
      if (declaredElements.length != 1) {
        return false;
      }
      final PsiElement declaredElement = declaredElements[0];
      if (!(declaredElement instanceof PsiVariable variable)) {
        return false;
      }
      final PsiExpression initializer = variable.getInitializer();
      return isArrayLookup(initializer, indexVariable, arrayVariable);
    }

    private static boolean isListElementDeclaration(PsiStatement statement, PsiVariable listVariable,
                                                    PsiVariable indexVariable) {
      if (!(statement instanceof PsiDeclarationStatement declarationStatement)) {
        return false;
      }
      final PsiElement[] declaredElements = declarationStatement.getDeclaredElements();
      if (declaredElements.length != 1) {
        return false;
      }
      final PsiElement declaredElement = declaredElements[0];
      if (!(declaredElement instanceof PsiVariable variable)) {
        return false;
      }
      final PsiExpression initializer = variable.getInitializer();
      return isListGetLookup(initializer, indexVariable, listVariable);
    }

    private static boolean isArrayLookup(PsiExpression expression, PsiVariable indexVariable, PsiVariable arrayVariable) {
      expression = PsiUtil.skipParenthesizedExprDown(expression);
      if (!(expression instanceof PsiArrayAccessExpression arrayAccess)) {
        return false;
      }
      final PsiExpression indexExpression = arrayAccess.getIndexExpression();
      if (!ExpressionUtils.isReferenceTo(indexExpression, indexVariable)) {
        return false;
      }
      final PsiExpression arrayExpression = PsiUtil.skipParenthesizedExprDown(arrayAccess.getArrayExpression());
      if (!(arrayExpression instanceof PsiReferenceExpression referenceExpression)) {
        return false;
      }
      final PsiExpression qualifier = PsiUtil.skipParenthesizedExprDown(referenceExpression.getQualifierExpression());
      if (qualifier != null && !(qualifier instanceof PsiThisExpression) && !(qualifier instanceof PsiSuperExpression)) {
        return false;
      }
      final PsiElement target = referenceExpression.resolve();
      return arrayVariable.equals(target);
    }
  }
}