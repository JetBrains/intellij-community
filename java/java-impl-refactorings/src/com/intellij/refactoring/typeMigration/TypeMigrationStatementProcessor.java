// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.typeMigration;

import com.intellij.codeInsight.generation.GetterSetterPrototypeProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.DefUseUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.intellij.refactoring.typeMigration.usageInfo.TypeMigrationUsageInfo;
import com.intellij.util.CommonProcessors;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * @author anna
 */
//return from lambda is processed inside visitReturnStatement
@SuppressWarnings("UnsafeReturnStatementVisitor")
class TypeMigrationStatementProcessor extends JavaRecursiveElementVisitor {
  private final PsiElement myStatement;
  private final TypeMigrationLabeler myLabeler;
  private static final Logger LOG = Logger.getInstance(TypeMigrationStatementProcessor.class);
  private final TypeEvaluator myTypeEvaluator;

  TypeMigrationStatementProcessor(PsiElement expression, TypeMigrationLabeler labeler) {
    myStatement = expression;
    myLabeler = labeler;
    myTypeEvaluator = myLabeler.getTypeEvaluator();
  }

  @Override
  public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression) {
    super.visitAssignmentExpression(expression);

    final PsiExpression lExpression = expression.getLExpression();
    final TypeView left = new TypeView(lExpression);

    final PsiExpression rExpression = expression.getRExpression();
    if (rExpression == null) return;
    final TypeView right = new TypeView(rExpression);

    final IElementType sign = expression.getOperationTokenType();
    final PsiType ltype = left.getType();
    final PsiType rtype = right.getType();
    if (ltype == null || rtype == null) return;

    if (sign != JavaTokenType.EQ) {
      final IElementType binaryOperator = TypeConversionUtil.convertEQtoOperation(sign);
      if (!TypeConversionUtil.isBinaryOperatorApplicable(binaryOperator, ltype, rtype, false)) {
        if (left.isChanged()) {
          findConversionOrFail(expression, lExpression, left.getTypePair());
        }
        if (right.isChanged()) {
          findConversionOrFail(expression, rExpression, right.getTypePair());
        }
        return;
      }
      if (binaryOperator == JavaTokenType.PLUS && !left.isChanged() && right.isChanged() &&
          ltype.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
        return;
      }
    }

    switch (TypeInfection.getInfection(left, right)) {
      case NONE_INFECTED -> {}
      case LEFT_INFECTED ->
        myLabeler.migrateExpressionType(rExpression, ltype, myStatement,
                                        TypeConversionUtil.isAssignable(ltype, rtype) && !isSetter(expression), true);
      case RIGHT_INFECTED -> {
        if (lExpression instanceof PsiReferenceExpression &&
            ((PsiReferenceExpression)lExpression).resolve() instanceof PsiLocalVariable &&
            !canBeVariableType(rtype)) {
          tryToRemoveLocalVariableAssignment((PsiLocalVariable)Objects.requireNonNull(((PsiReferenceExpression)lExpression).resolve()),
                                             rExpression, rtype);
        }
        else {
          myLabeler.migrateExpressionType(lExpression, rtype, myStatement, TypeConversionUtil.isAssignable(ltype, rtype), false);
        }
      }
      case BOTH_INFECTED -> {
        addTypeUsage(lExpression);
        addTypeUsage(rExpression);
      }
    }
  }

  @Override
  public void visitArrayAccessExpression(@NotNull PsiArrayAccessExpression expression) {
    super.visitArrayAccessExpression(expression);
    final PsiExpression indexExpression = expression.getIndexExpression();
    if (indexExpression != null) {
      checkIndexExpression(indexExpression);
    }
    final TypeView typeView = new TypeView(expression.getArrayExpression());
    if (typeView.isChanged() && typeView.getType() instanceof PsiClassType) {
      final TypeConversionDescriptorBase conversion =
        myLabeler.getRules().findConversion(typeView.getTypePair().first, typeView.getType(), null, expression, false, myLabeler);

      if (conversion == null) {
        myLabeler.markFailedConversion(typeView.getType(), expression);
      }
      else {
        myLabeler.setConversionMapping(expression, conversion);
        PsiType type = myTypeEvaluator.evaluateType(expression);
        if (type != null) {
          myTypeEvaluator.setType(new TypeMigrationUsageInfo(expression), type);
        }
      }

    }
  }

  @Override
  public void visitSwitchLabelStatement(@NotNull PsiSwitchLabelStatement statement) {
    super.visitSwitchLabelStatement(statement);
    final PsiExpression caseValue = statement.getCaseValue();
    if (caseValue != null) {
      final TypeView typeView = new TypeView(caseValue);
      if (typeView.isChanged()) {
        final PsiSwitchStatement switchStatement = statement.getEnclosingSwitchStatement();
        if (switchStatement != null) {
          final PsiExpression expression = switchStatement.getExpression();
          if (expression != null) {
            myLabeler.migrateExpressionType(expression, typeView.getType(), myStatement, false, false);
          }
        }
      }
    }
  }

  @Override
  public void visitInstanceOfExpression(@NotNull PsiInstanceOfExpression expression) {
    super.visitInstanceOfExpression(expression);
    final PsiTypeElement typeElement = expression.getCheckType();
    if (typeElement != null) {
      final PsiExpression consideredExpression = expression.getOperand();
      final PsiType migrationType = myTypeEvaluator.evaluateType(consideredExpression);
      final PsiType fixedType = typeElement.getType();
      if (migrationType != null && !TypeConversionUtil.isAssignable(migrationType, fixedType)) {
        myLabeler.markFailedConversion(migrationType, consideredExpression);
      }
    }
  }

  @Override
  public void visitTypeCastExpression(@NotNull PsiTypeCastExpression expression) {
    super.visitTypeCastExpression(expression);
    final PsiTypeElement typeElement = expression.getCastType();
    if (typeElement != null) {
      final PsiType fixedType = typeElement.getType();
      final PsiType migrationType = myTypeEvaluator.evaluateType(expression.getOperand());
      if (migrationType != null && !TypeConversionUtil.areTypesConvertible(migrationType, fixedType)) {
        myLabeler.markFailedConversion(migrationType, expression);
      }
    }
  }

  @Override
  public void visitVariable(@NotNull PsiVariable variable) {
    super.visitVariable(variable);

    final PsiExpression initializer = variable.getInitializer();

    if (initializer != null && initializer.getType() != null) {
      processVariable(variable, initializer, null, null, null, false);
    }
  }

  @Override
  public void visitReturnStatement(@NotNull PsiReturnStatement statement) { // has to change method return type corresponding to new value type
    super.visitReturnStatement(statement);

    final PsiElement method = PsiTreeUtil.getParentOfType(statement, PsiMethod.class, PsiLambdaExpression.class);
    final PsiExpression value = statement.getReturnValue();

    if (method != null && value != null) {
      if (method instanceof PsiLambdaExpression) {
        //todo [IDEA-133097]
        return;
      }
      final PsiType returnType = ((PsiMethod)method).getReturnType();
      final PsiType valueType = myTypeEvaluator.evaluateType(value);
      if (returnType != null && valueType != null) {
        if ((isGetter(value, method) || !TypeConversionUtil.isAssignable(returnType, valueType))
            && returnType.equals(myTypeEvaluator.getType(method))
            && !myLabeler.addMigrationRoot(method, valueType, myStatement, false, true)) {
          myLabeler.convertExpression(value, valueType, returnType, false);
        }
      }
    }
  }

  @Override
  public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
    final PsiExpression qualifierExpression = expression.getQualifierExpression();

    if (qualifierExpression != null && qualifierExpression.isPhysical()) {
      qualifierExpression.accept(this);

      final TypeView qualifierView = new TypeView(qualifierExpression);

      if (qualifierView.isChanged()) {
        final PsiMember member = (PsiMember)expression.advancedResolve(false).getElement();
        if (member == null) return;
        final Pair<PsiType, PsiType> typePair = qualifierView.getTypePair();

        final TypeConversionDescriptorBase conversion = myLabeler.getRules().findConversion(typePair.getFirst(), typePair.getSecond(), member, expression, false, myLabeler);

        if (conversion == null) {
          myLabeler.markFailedConversion(typePair.getSecond(), qualifierExpression);
        } else {
          final PsiElement parent = Util.getEssentialParent(expression);
          final PsiType type = conversion.conversionType();
          if (parent instanceof PsiMethodCallExpression) {
            myLabeler.setConversionMapping((PsiMethodCallExpression)parent, conversion);
            PsiType targetType = type != null ? type : myTypeEvaluator.evaluateType((PsiExpression)parent);
            if (targetType != null) {
              myTypeEvaluator.setType(new TypeMigrationUsageInfo(parent), targetType);
            }
          } else {
            myLabeler.setConversionMapping(expression, conversion);
            PsiType targetType = type != null ? type : myTypeEvaluator.evaluateType(expression);
            if (targetType != null) {
              myTypeEvaluator.setType(new TypeMigrationUsageInfo(expression), targetType);
            }
          }
        }
      }
    }
    else if (PsiUtil.isCondition(expression, expression.getParent())) {
      final TypeView view = new TypeView(expression);
      if (view.isChanged()) { //means that boolean condition becomes non-boolean
        findConversionOrFail(expression, expression, view.getTypePair());
      }
    }
  }

  @Override
  public void visitIfStatement(@NotNull PsiIfStatement statement) {
    super.visitIfStatement(statement);
    final PsiExpression condition = statement.getCondition();
    if (condition != null) {
      final TypeView view = new TypeView(condition);
      if (view.isChanged()) { //means that boolean condition becomes non-boolean
        findConversionOrFail(condition, condition, view.getTypePair());
      }
    }
  }

  @Override
  public void visitForeachStatement(@NotNull PsiForeachStatement statement) {
    super.visitForeachStatement(statement);
    final PsiExpression value = statement.getIteratedValue();
    final PsiParameter psiParameter = statement.getIterationParameter();
    if (value != null) {
      final TypeView typeView = new TypeView(value);
      PsiType psiType = typeView.getType();
      if (psiType instanceof PsiArrayType) {
        psiType = ((PsiArrayType)psiType).getComponentType();
      }
      else if (psiType instanceof PsiClassType) {
        final PsiClassType.ClassResolveResult resolveResult = ((PsiClassType)psiType).resolveGenerics();
        final PsiClass psiClass = resolveResult.getElement();
        if (psiClass == null) return;
        final PsiType targetTypeParameter = getTargetTypeParameter(psiClass, value, typeView);
        if (targetTypeParameter == null) return;
        psiType = resolveResult.getSubstitutor().substitute(targetTypeParameter);
        if (psiType instanceof PsiWildcardType) {
          psiType = ((PsiWildcardType)psiType).getExtendsBound();
        }
      }
      else {
        myLabeler.markFailedConversion(typeView.getType(), value);
        return;
      }
      final TypeView left = new TypeView(psiParameter, null, null);
      if (TypeInfection.getInfection(left, typeView) == TypeInfection.LEFT_INFECTED) {
        PsiType iterableType;
        final PsiType typeViewType = typeView.getType();
        if (typeViewType instanceof PsiArrayType) {
          iterableType = left.getType().createArrayType();
        } else {
          final PsiClass iterableClass = PsiUtil.resolveClassInType(typeViewType);
          LOG.assertTrue(iterableClass != null);

          final PsiType targetType = getTargetTypeParameter(iterableClass, value, typeView);
          final PsiClass typeParam = PsiUtil.resolveClassInClassTypeOnly(targetType);
          if (!(typeParam instanceof PsiTypeParameter)) return;

          final Map<PsiTypeParameter, PsiType> substMap = Collections.singletonMap(((PsiTypeParameter)typeParam), left.getType());

          final PsiElementFactory factory = JavaPsiFacade.getElementFactory(iterableClass.getProject());
          iterableType = factory.createType(iterableClass, factory.createSubstitutor(substMap));
        }
        myLabeler.migrateExpressionType(value, iterableType, myStatement, TypeConversionUtil.isAssignable(iterableType, typeViewType), true);
      } else {
        processVariable(psiParameter, value, psiType, null, null, false);
      }
    }
  }

  private PsiType getTargetTypeParameter(PsiClass iterableClass, PsiExpression value, TypeView typeView) {
    final Project project = iterableClass.getProject();
    final PsiClass itClass =
      JavaPsiFacade.getInstance(project).findClass("java.lang.Iterable", GlobalSearchScope.allScope(project));
    if (itClass == null) return null;

    if (!InheritanceUtil.isInheritorOrSelf(iterableClass, itClass, true)) {
      findConversionOrFail(value, value, typeView.getTypePair());
      return null;
    }

    final PsiSubstitutor aSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(itClass, iterableClass, PsiSubstitutor.EMPTY);

    return aSubstitutor.substitute(itClass.getTypeParameters()[0]);
  }

  @Override
  public void visitNewExpression(@NotNull PsiNewExpression expression) {
    super.visitNewExpression(expression);
    final PsiExpression[] dimensions = expression.getArrayDimensions();
    for (PsiExpression dimension : dimensions) {
      checkIndexExpression(dimension);
    }
    final PsiArrayInitializerExpression arrayInitializer = expression.getArrayInitializer();
    if (arrayInitializer != null) {
      processArrayInitializer(arrayInitializer, expression);
    }
  }

  @Override
  public void visitArrayInitializerExpression(@NotNull PsiArrayInitializerExpression expression) {
    super.visitArrayInitializerExpression(expression);
    processArrayInitializer(expression, expression);
  }

  @Override
  public void visitUnaryExpression(@NotNull PsiUnaryExpression expression) {
    super.visitUnaryExpression(expression);
    final TypeView typeView = new TypeView(expression);
    if (typeView.isChanged()) {
      if (!TypeConversionUtil.isUnaryOperatorApplicable(expression.getOperationSign(), typeView.getType())) {
        findConversionOrFail(expression, expression, typeView.getTypePair());
      }
    }
  }

  private void findConversionOrFail(PsiExpression expression, PsiExpression toFail, Pair<PsiType, PsiType> typePair) {
    final TypeConversionDescriptorBase conversion = myLabeler.getRules().findConversion(typePair.getFirst(), typePair.getSecond(), null, expression, myLabeler);
    if (conversion == null) {
      myLabeler.markFailedConversion(typePair.getSecond(), toFail);
    }
    else {
      myLabeler.setConversionMapping(expression, conversion);
      final PsiType psiType = myTypeEvaluator.evaluateType(expression);
      LOG.assertTrue(psiType != null,  expression);
      myTypeEvaluator.setType(new TypeMigrationUsageInfo(expression), psiType);
    }
  }

  @Override
  public void visitPolyadicExpression(@NotNull PsiPolyadicExpression expression) {
    super.visitPolyadicExpression(expression);
    final PsiExpression[] operands = expression.getOperands();
    if (operands.length == 0) return;
    final IElementType operationTokenType = expression.getOperationTokenType();

    PsiExpression lOperand = operands[0];
    TypeView left = new TypeView(lOperand);
    for(int i = 1; i < operands.length; i++) {
      final PsiExpression rOperand = operands[i];
      if (rOperand == null) return;
      final TypeView right = new TypeView(rOperand);
      if (tryFindConversionIfOperandIsNull(left, right, rOperand)) continue;
      if (tryFindConversionIfOperandIsNull(right, left, lOperand)) continue;
      if (!TypeConversionUtil.isBinaryOperatorApplicable(operationTokenType, left.getType(), right.getType(), false)) {
        if (left.isChanged()) {
          findConversionOrFail(lOperand, lOperand, left.getTypePair());
        }
        if (right.isChanged()) {
          findConversionOrFail(rOperand, rOperand, right.getTypePair());
        }
      }
      lOperand = rOperand;
      left = right;
    }
  }

  protected boolean tryFindConversionIfOperandIsNull(TypeView nullCandidate, TypeView comparingType, PsiExpression comparingExpr) {
    if (nullCandidate.getType() == PsiTypes.nullType() && comparingType.isChanged()) {
      Pair<PsiType, PsiType> typePair = comparingType.getTypePair();
      final TypeConversionDescriptorBase
        conversion = myLabeler.getRules().findConversion(typePair.getFirst(), typePair.getSecond(), null, comparingExpr, false, myLabeler);
      if (conversion != null) {
        myLabeler.setConversionMapping(comparingExpr, conversion);
      }
      return true;
    }
    return false;
  }

  private void processArrayInitializer(PsiArrayInitializerExpression expression, PsiExpression parentExpression) {
    final PsiExpression[] initializers = expression.getInitializers();
    PsiType migrationType = null;
    for (PsiExpression initializer : initializers) {
      final TypeView typeView = new TypeView(initializer);
      if (typeView.isChanged()) {
        final PsiType type = typeView.getType();
        if (migrationType == null || !TypeConversionUtil.isAssignable(migrationType, type)) {
          if (migrationType != null && !TypeConversionUtil.isAssignable(type, migrationType)) {
            myLabeler.markFailedConversion(type, parentExpression);
            return;
          }
          migrationType = type;
        }
      }
    }
    final PsiType exprType = expression.getType();
    if (migrationType != null && exprType instanceof PsiArrayType) {
      final boolean alreadyProcessed = TypeConversionUtil.isAssignable(((PsiArrayType)exprType).getComponentType(), migrationType);
      myLabeler.migrateExpressionType(parentExpression, alreadyProcessed ? exprType : migrationType.createArrayType(), expression, alreadyProcessed, true);
    }
  }

  private void checkIndexExpression(PsiExpression indexExpression) {
    final PsiType indexType = myTypeEvaluator.evaluateType(indexExpression);
    if (indexType != null && !TypeConversionUtil.isAssignable(PsiTypes.intType(), indexType)) {
      myLabeler.markFailedConversion(indexType, indexExpression);
    }
  }

  @Override
  public void visitCallExpression(@NotNull PsiCallExpression callExpression) {
    super.visitCallExpression(callExpression);
    final JavaResolveResult resolveResult = callExpression.resolveMethodGenerics();
    final PsiElement method = resolveResult.getElement();
    if (method instanceof PsiMethod) {
      if (callExpression instanceof PsiMethodCallExpression && migrateEqualsMethod((PsiMethodCallExpression)callExpression, (PsiMethod)method)) {
        return;
      }
      PsiExpressionList argumentList = callExpression.getArgumentList();
      if (argumentList == null) return;
      final PsiExpression[] psiExpressions = argumentList.getExpressions();
      final PsiParameter[] originalParams = ((PsiMethod)method).getParameterList().getParameters();
      final PsiSubstitutor evalSubstitutor = myTypeEvaluator.createMethodSubstitution(originalParams, psiExpressions, (PsiMethod)method, callExpression);
      for (int i = 0; i < psiExpressions.length; i++) {
        PsiParameter originalParameter;
        if (originalParams.length <= i) {
          if (originalParams.length > 0 && originalParams[originalParams.length - 1].isVarArgs()) {
            originalParameter = originalParams[originalParams.length - 1];
          } else {
            continue;
          }
        }
        else {
          originalParameter = originalParams[i];
        }
        processVariable(originalParameter, psiExpressions[i], null, resolveResult.getSubstitutor(), evalSubstitutor, true);
      }
      final PsiExpression qualifier = callExpression instanceof PsiMethodCallExpression ? ((PsiMethodCallExpression)callExpression).getMethodExpression().getQualifierExpression() : null;
      if (qualifier != null && qualifier.isPhysical() && !new TypeView(qualifier).isChanged()) { //substitute property otherwise
        final PsiType qualifierType = qualifier.getType();
        if (qualifierType instanceof PsiClassType) {
          final PsiClassType.ClassResolveResult classResolveResult = ((PsiClassType)qualifierType).resolveGenerics();
          final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(myStatement.getProject());
          PsiClass psiClass = classResolveResult.getElement();
          if (psiClass == null) return;
          final PsiType migrationType = elementFactory.createType(psiClass, composeIfNotAssignable(classResolveResult.getSubstitutor(), evalSubstitutor));
          myLabeler.migrateExpressionType(qualifier, migrationType, myStatement, migrationType.equals(qualifierType), true);
        }
      }
    }
  }

  private boolean migrateEqualsMethod(PsiMethodCallExpression methodCallExpression, PsiMethod method) {
    final PsiExpression qualifier = methodCallExpression.getMethodExpression().getQualifierExpression();
    if (qualifier == null) {
      return false;
    }
    final TypeView qualifierTypeView = new TypeView(qualifier);
    if (!qualifierTypeView.isChanged()) {
      return false;
    }
    if (method.getName().equals("equals") && method.getParameterList().getParametersCount() == 1) {
      final PsiParameter parameter = method.getParameterList().getParameters()[0];
      if (parameter.getType().equals(PsiType.getJavaLangObject(methodCallExpression.getManager(), methodCallExpression.getResolveScope()))) {
        final PsiExpression[] expressions = methodCallExpression.getArgumentList().getExpressions();
        if (expressions.length != 1) {
          return false;
        }
        final TypeView argumentTypeView = new TypeView(expressions[0]);
        final PsiType argumentType = argumentTypeView.getType();
        if (!argumentTypeView.isChanged() && qualifierTypeView.getTypePair().getFirst().equals(argumentType)) {
          final PsiType migrationType = qualifierTypeView.getType();
          myLabeler.migrateExpressionType(expressions[0],
                                          migrationType,
                                          methodCallExpression,
                                          TypeConversionUtil.isAssignable(migrationType, argumentType),
                                          true);
          return true;
        }
      }
    }
    return false;
  }

  private void processVariable(PsiVariable variable,
                               PsiExpression value,
                               PsiType migrationType,
                               PsiSubstitutor varSubstitutor,
                               PsiSubstitutor evalSubstitutor,
                               boolean isCovariantPosition) {
    final TypeView right = new TypeView(value);
    final TypeView left = new TypeView(variable, varSubstitutor, evalSubstitutor);
    PsiType declarationType = left.getType();

    switch (TypeInfection.getInfection(left, right)) {
      case NONE_INFECTED -> {}
      case LEFT_INFECTED -> {
        final PsiType valueType = right.getType();
        if (valueType != null && declarationType != null) {
          myLabeler.migrateExpressionType(value,
                                          adjustMigrationTypeIfGenericArrayCreation(declarationType, value),
                                          myStatement,
                                          left.isVarArgs()
                                          ? isVarargAssignable(left, right)
                                          : TypeConversionUtil.isAssignable(declarationType, valueType), true);
        }
      }
      case RIGHT_INFECTED -> {
        PsiType psiType = migrationType != null ? migrationType : right.getType();
        if (psiType != null) {
          if (canBeVariableType(psiType)) {
            if (declarationType != null) {
              boolean assignable = left.isVarArgs()
                                  ? isVarargAssignable(left, right)
                                  : TypeConversionUtil.isAssignable(declarationType, psiType);
              PsiType newType = left.isVarArgs() && !right.isVarArgs() ? new PsiEllipsisType(psiType) : psiType;
              if (!myLabeler.addMigrationRoot(variable, newType, myStatement, assignable, true) && !assignable) {
                if (declarationType instanceof PsiEllipsisType) {
                  declarationType = ((PsiEllipsisType)declarationType).getComponentType();
                }
                myLabeler.convertExpression(value, psiType, declarationType, isCovariantPosition);
              }
            }
          }
          else {
            if (variable instanceof PsiLocalVariable) {
              final PsiDeclarationStatement decl = PsiTreeUtil.getParentOfType(variable, PsiDeclarationStatement.class);
              if (decl != null && decl.getDeclaredElements().length == 1) {
                tryToRemoveLocalVariableAssignment((PsiLocalVariable)variable, value, psiType);
              }
            }
          }
        }
      }
      case BOTH_INFECTED -> addTypeUsage(variable);
    }
  }

  private void tryToRemoveLocalVariableAssignment(@NotNull PsiLocalVariable variable, @NotNull PsiExpression valueExpression, @NotNull PsiType migrationType) {
    final PsiCodeBlock codeBlock = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
    final PsiElement[] refs = DefUseUtil.getRefs(Objects.requireNonNull(codeBlock), variable, valueExpression);
    if (refs.length == 0) {
      myLabeler.setConversionMapping(valueExpression, new TypeConversionDescriptorBase() {
        @Override
        public PsiExpression replace(PsiExpression expression, @NotNull TypeEvaluator evaluator) throws IncorrectOperationException {
          final PsiElement parent = expression.getParent();
          if (parent instanceof PsiLocalVariable var) {
            final PsiDeclarationStatement decl = PsiTreeUtil.getParentOfType(var, PsiDeclarationStatement.class);
            if (decl == null) return null;
            final Project project = var.getProject();
            final PsiAssignmentExpression assignment = ExpressionUtils.splitDeclaration(decl, project);
            if (assignment == null) return null;
            final PsiExpression rExpression = assignment.getRExpression();
            if (rExpression == null) return null;
            assignment.replace(rExpression);
            if (ReferencesSearch.search(var).forEach(new CommonProcessors.FindFirstProcessor<>())) {
              var.delete();
            }
          }
          else if (parent instanceof PsiAssignmentExpression assignment) {
            final PsiExpression rExpression = assignment.getRExpression();
            return rExpression == null ? null : (PsiExpression)parent.replace(rExpression);
          }
          return null;
        }
      });
    } else {
      myLabeler.markFailedConversion(migrationType, valueExpression);
    }
  }

  private static boolean canBeVariableType(@NotNull PsiType type) {
    return !type.getDeepComponentType().equals(PsiTypes.voidType());
  }

  private static PsiType adjustMigrationTypeIfGenericArrayCreation(PsiType migrationType, PsiExpression expression) {
    if (expression instanceof PsiNewExpression) {
      if (migrationType instanceof PsiArrayType) {
        final PsiType componentType = migrationType.getDeepComponentType();
        if (componentType instanceof PsiClassType) {
          final PsiClassType rawType = ((PsiClassType)componentType).rawType();
          if (!rawType.equals(componentType)) {
            return PsiTypesUtil.createArrayType(rawType, migrationType.getArrayDimensions());
          }
        }
      }
    }
    return migrationType;
  }


  private void addTypeUsage(PsiElement typedElement) {
    if (typedElement instanceof PsiReferenceExpression) {
      myLabeler.setTypeUsage(((PsiReferenceExpression)typedElement).resolve(), myStatement);
    }
    else if (typedElement instanceof PsiMethodCallExpression) {
      myLabeler.setTypeUsage(((PsiMethodCallExpression)typedElement).resolveMethod(), myStatement);
    }
    else {
      myLabeler.setTypeUsage(typedElement, myStatement);
    }
  }


  private class TypeView {
    final PsiType myOriginType;
    final PsiType myType;
    final boolean myChanged;

    TypeView(@NotNull PsiExpression expr) {
      PsiType exprType = expr.getType();
      myOriginType = GenericsUtil.getVariableTypeByExpressionType(exprType);
      PsiType type = myTypeEvaluator.evaluateType(expr);
      myType = GenericsUtil.getVariableTypeByExpressionType(type);
      myChanged = !(myOriginType == null || myType == null) && !myType.equals(myOriginType);
    }

    TypeView(PsiVariable var, PsiSubstitutor varSubstitutor, PsiSubstitutor evalSubstitutor) {
      myOriginType = varSubstitutor != null ? varSubstitutor.substitute(var.getType()) : var.getType();

      PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
      if (varSubstitutor != null) substitutor = substitutor.putAll(varSubstitutor);
      if (evalSubstitutor != null) substitutor = substitutor.putAll(evalSubstitutor);

      myType = substitutor.substitute(myTypeEvaluator.getType(var));
      myChanged = !(myOriginType == null || myType == null) && !myType.equals(myOriginType);
    }

    public PsiType getType() {
      return myType;
    }

    public boolean isChanged() {
      return myChanged;
    }

    public Pair<PsiType, PsiType> getTypePair() {
      return Pair.create(myOriginType, myType);
    }

    public boolean isVarArgs() {
      return myType instanceof PsiEllipsisType && myOriginType instanceof PsiEllipsisType;
    }
  }

  private enum TypeInfection {
    NONE_INFECTED,
    LEFT_INFECTED,
    RIGHT_INFECTED,
    BOTH_INFECTED;

    static TypeInfection getInfection(TypeView left, TypeView right) {
      if (left.isChanged()) {
        return right.isChanged() ? BOTH_INFECTED : LEFT_INFECTED;
      }
      else {
        return right.isChanged() ? RIGHT_INFECTED : NONE_INFECTED;
      }
    }
  }

  private static boolean isSetter(PsiAssignmentExpression expression) {
    final PsiExpression lExpression = expression.getLExpression();
    if (lExpression instanceof PsiReferenceExpression) {
      final PsiElement resolved = ((PsiReferenceExpression)lExpression).resolve();
      if (resolved instanceof PsiField field) {
        final NavigatablePsiElement containingMethod = PsiTreeUtil.getParentOfType(expression, PsiMethod.class, PsiLambdaExpression.class);
        if (containingMethod instanceof PsiMethod) {
          final PsiMethod setter = PropertyUtilBase
            .findPropertySetter(field.getContainingClass(), field.getName(), field.hasModifierProperty(PsiModifier.STATIC), false);
          if (containingMethod.isEquivalentTo(setter)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private static boolean isGetter(PsiExpression returnValue, PsiElement containingMethod) {
    if (returnValue instanceof PsiReferenceExpression) {
      final PsiElement resolved = ((PsiReferenceExpression)returnValue).resolve();
      if (resolved instanceof PsiField field) {
        final boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
        final PsiMethod[] getters = GetterSetterPrototypeProvider.findGetters(field.getContainingClass(), field.getName(), isStatic);
        if (getters != null) {
          for (PsiMethod getter : getters) {
            if (containingMethod.isEquivalentTo(getter)) {
              return true;
            }
          }
        }
      }
    }
    return false;
  }

  private static PsiSubstitutor composeIfNotAssignable(PsiSubstitutor actual, PsiSubstitutor required) {
    if (actual == PsiSubstitutor.EMPTY) {
      return required;
    }
    if (required == PsiSubstitutor.EMPTY) {
      return actual;
    }
    PsiSubstitutor result = PsiSubstitutor.createSubstitutor(actual.getSubstitutionMap());
    for (Map.Entry<PsiTypeParameter, PsiType> e : required.getSubstitutionMap().entrySet()) {
      final PsiTypeParameter typeParameter = e.getKey();
      final PsiType requiredType = e.getValue();
      final PsiType actualType = result.getSubstitutionMap().get(typeParameter);
      if (requiredType != null && (actualType == null || !TypeConversionUtil.isAssignable(actualType, requiredType))) {
        result = result.put(typeParameter, requiredType);
      }
    }
    return result;
  }

  private static boolean isVarargAssignable(TypeView left, TypeView right) {
    Pair<PsiType, PsiType> leftPair = left.getTypePair();
    Pair<PsiType, PsiType> rightPair = right.getTypePair();

    PsiType leftOrigin = leftPair.getFirst();
    PsiType rightOrigin = rightPair.getFirst();

    boolean isDirectlyAssignable = TypeConversionUtil.isAssignable(leftOrigin, rightOrigin);

    return TypeConversionUtil.isAssignable(isDirectlyAssignable ?
                                           leftPair.getSecond() :
                                           ((PsiEllipsisType)leftPair.getSecond()).getComponentType(), rightPair.getSecond());
  }
}
