/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.refactoring.typeMigration;

import com.intellij.codeInsight.generation.GetterSetterPrototypeProvider;
import com.intellij.codeInsight.intention.impl.SplitDeclarationAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.DefUseUtil;
import com.intellij.psi.impl.PsiSubstitutorImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.intellij.refactoring.typeMigration.usageInfo.TypeMigrationUsageInfo;
import com.intellij.util.CommonProcessors;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;

/**
 * @author anna
 */
class TypeMigrationStatementProcessor extends JavaRecursiveElementVisitor {
  private final PsiElement myStatement;
  private final TypeMigrationLabeler myLabeler;
  private static final Logger LOG = Logger.getInstance(TypeMigrationStatementProcessor.class);
  private final TypeEvaluator myTypeEvaluator;

  public TypeMigrationStatementProcessor(final PsiElement expression, TypeMigrationLabeler labeler) {
    myStatement = expression;
    myLabeler = labeler;
    myTypeEvaluator = myLabeler.getTypeEvaluator();
  }

  @Override
  public void visitAssignmentExpression(PsiAssignmentExpression expression) {
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
    }

    switch (TypeInfection.getInfection(left, right)) {
      case TypeInfection.NONE_INFECTED:
        break;

      case TypeInfection.LEFT_INFECTED:
        myLabeler.migrateExpressionType(rExpression, ltype, myStatement, TypeConversionUtil.isAssignable(ltype, rtype) && !isSetter(expression), true);
        break;

      case TypeInfection.RIGHT_INFECTED:
        if (lExpression instanceof PsiReferenceExpression &&
            ((PsiReferenceExpression)lExpression).resolve() instanceof PsiLocalVariable &&
            !canBeVariableType(rtype)) {
          tryToRemoveLocalVariableAssignment((PsiLocalVariable)((PsiReferenceExpression)lExpression).resolve(), rExpression, rtype);
        } else {
          myLabeler.migrateExpressionType(lExpression, rtype, myStatement, TypeConversionUtil.isAssignable(ltype, rtype), false);
        }
        break;

      case TypeInfection.BOTH_INFECTED:
        addTypeUsage(lExpression);
        addTypeUsage(rExpression);
        break;

      default:
        LOG.error("Must not happen.");
    }
  }

  @Override
  public void visitArrayAccessExpression(final PsiArrayAccessExpression expression) {
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
        myLabeler.markFailedConversion(typeView.getTypePair(), expression);
      }
      else {
        myLabeler.setConversionMapping(expression, conversion);
        myTypeEvaluator.setType(new TypeMigrationUsageInfo(expression), myTypeEvaluator.evaluateType(expression));
      }

    }
  }

  @Override
  public void visitSwitchLabelStatement(PsiSwitchLabelStatement statement) {
    super.visitSwitchLabelStatement(statement);
    final PsiExpression caseValue = statement.getCaseValue();
    if (caseValue != null) {
      final TypeView typeView = new TypeView(caseValue);
      if (typeView.isChanged()) {
        final PsiSwitchStatement switchStatement = statement.getEnclosingSwitchStatement();
        if (switchStatement != null) {
          final PsiExpression expression = switchStatement.getExpression();
          myLabeler.migrateExpressionType(expression, typeView.getType(), myStatement, false, false);
        }
      }
    }
  }

  @Override
  public void visitInstanceOfExpression(final PsiInstanceOfExpression expression) {
    super.visitInstanceOfExpression(expression);
    final PsiTypeElement typeElement = expression.getCheckType();
    if (typeElement != null) {
      final PsiExpression consideredExpression = expression.getOperand();
      final PsiType migrationType = myTypeEvaluator.evaluateType(consideredExpression);
      final PsiType fixedType = typeElement.getType();
      if (migrationType != null && !TypeConversionUtil.isAssignable(migrationType, fixedType)) {
        myLabeler.markFailedConversion(Pair.create(fixedType, migrationType), consideredExpression);
      }
    }
  }

  @Override
  public void visitTypeCastExpression(final PsiTypeCastExpression expression) {
    super.visitTypeCastExpression(expression);
    final PsiTypeElement typeElement = expression.getCastType();
    if (typeElement != null) {
      final PsiType fixedType = typeElement.getType();
      final PsiType migrationType = myTypeEvaluator.evaluateType(expression.getOperand());
      if (migrationType != null && !TypeConversionUtil.areTypesConvertible(migrationType, fixedType)) {
        myLabeler.markFailedConversion(Pair.create(fixedType, migrationType), expression);
      }
    }
  }

  @Override
  public void visitVariable(PsiVariable variable) {
    super.visitVariable(variable);

    final PsiExpression initializer = variable.getInitializer();

    if (initializer != null && initializer.getType() != null) {
      processVariable(variable, initializer, null, null, null, false);
    }
  }

  @Override
  public void visitReturnStatement(final PsiReturnStatement statement) { // has to change method return type corresponding to new value type
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
  public void visitReferenceExpression(PsiReferenceExpression expression) {
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
          myLabeler.markFailedConversion(typePair, qualifierExpression);
        } else {
          final PsiElement parent = Util.getEssentialParent(expression);
          final PsiType type = conversion.conversionType();
          if (parent instanceof PsiMethodCallExpression) {
            myLabeler.setConversionMapping((PsiMethodCallExpression)parent, conversion);
            myTypeEvaluator.setType(new TypeMigrationUsageInfo(parent), type != null ? type: myTypeEvaluator.evaluateType((PsiExpression)parent));
          } else {
            myLabeler.setConversionMapping(expression, conversion);
            myTypeEvaluator.setType(new TypeMigrationUsageInfo(expression), type != null ? type: myTypeEvaluator.evaluateType(expression));
          }
        }
      }
    }
  }

  @Override
  public void visitIfStatement(PsiIfStatement statement) {
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
  public void visitForeachStatement(final PsiForeachStatement statement) {
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
        final PsiType targetTypeParameter = getTargetTypeParameter(psiClass, value, typeView);
        if (targetTypeParameter == null) return;
        psiType = resolveResult.getSubstitutor().substitute(targetTypeParameter);
        if (psiType instanceof PsiWildcardType) {
          psiType = ((PsiWildcardType)psiType).getExtendsBound();
        }
      }
      else {
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
  public void visitNewExpression(final PsiNewExpression expression) {
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
  public void visitArrayInitializerExpression(final PsiArrayInitializerExpression expression) {
    super.visitArrayInitializerExpression(expression);
    processArrayInitializer(expression, expression);
  }

  @Override
  public void visitUnaryExpression(final PsiUnaryExpression expression) {
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
      myLabeler.markFailedConversion(typePair, toFail);
    }
    else {
      myLabeler.setConversionMapping(expression, conversion);
      final PsiType psiType = myTypeEvaluator.evaluateType(expression);
      LOG.assertTrue(psiType != null,  expression);
      myTypeEvaluator.setType(new TypeMigrationUsageInfo(expression), psiType);
    }
  }

  @Override
  public void visitPolyadicExpression(PsiPolyadicExpression expression) {
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
    if (nullCandidate.getType() == PsiType.NULL && comparingType.isChanged()) {
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

  private void processArrayInitializer(final PsiArrayInitializerExpression expression, final PsiExpression parentExpression) {
    final PsiExpression[] initializers = expression.getInitializers();
    PsiType migrationType = null;
    for (PsiExpression initializer : initializers) {
      final TypeView typeView = new TypeView(initializer);
      if (typeView.isChanged()) {
        final PsiType type = typeView.getType();
        if (migrationType == null || !TypeConversionUtil.isAssignable(migrationType, type)) {
          if (migrationType != null && !TypeConversionUtil.isAssignable(type, migrationType)) {
            myLabeler.markFailedConversion(Pair.create(parentExpression.getType(), type), parentExpression);
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

  private void checkIndexExpression(final PsiExpression indexExpression) {
    final PsiType indexType = myTypeEvaluator.evaluateType(indexExpression);
    if (indexType != null && !TypeConversionUtil.isAssignable(PsiType.INT, indexType)) {
      myLabeler.markFailedConversion(Pair.create(indexExpression.getType(), indexType), indexExpression);
    }
  }

  @Override
  public void visitMethodCallExpression(final PsiMethodCallExpression methodCallExpression) {
    super.visitMethodCallExpression(methodCallExpression);
    final JavaResolveResult resolveResult = methodCallExpression.resolveMethodGenerics();
    final PsiElement method = resolveResult.getElement();
    if (method instanceof PsiMethod) {
      if (migrateEqualsMethod(methodCallExpression, (PsiMethod)method)) {
        return;
      }
      final PsiExpression[] psiExpressions = methodCallExpression.getArgumentList().getExpressions();
      final PsiParameter[] originalParams = ((PsiMethod)method).getParameterList().getParameters();
      final PsiSubstitutor evalSubstitutor = myTypeEvaluator.createMethodSubstitution(originalParams, psiExpressions, (PsiMethod)method, methodCallExpression);
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
      final PsiExpression qualifier = methodCallExpression.getMethodExpression().getQualifierExpression();
      if (qualifier != null && qualifier.isPhysical() && !new TypeView(qualifier).isChanged()) { //substitute property otherwise
        final PsiType qualifierType = qualifier.getType();
        if (qualifierType instanceof PsiClassType) {
          final PsiClassType.ClassResolveResult classResolveResult = ((PsiClassType)qualifierType).resolveGenerics();
          final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(myStatement.getProject());
          final PsiType migrationType = elementFactory.createType(classResolveResult.getElement(), composeIfNotAssignable(classResolveResult.getSubstitutor(), evalSubstitutor));
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

  private void processVariable(final PsiVariable variable,
                               final PsiExpression value,
                               final PsiType migrationType,
                               final PsiSubstitutor varSubstitutor,
                               final PsiSubstitutor evalSubstitutor,
                               final boolean isCovariantPosition) {
    final TypeView right = new TypeView(value);
    final TypeView left = new TypeView(variable, varSubstitutor, evalSubstitutor);
    final PsiType declarationType = left.getType();

    switch (TypeInfection.getInfection(left, right)) {
      case TypeInfection.NONE_INFECTED:
        break;

      case TypeInfection.LEFT_INFECTED:
        final PsiType valueType = right.getType();
        if (valueType != null && declarationType != null) {
          myLabeler.migrateExpressionType(value,
                                          adjustMigrationTypeIfGenericArrayCreation(declarationType, value),
                                          myStatement,
                                          left.isVarArgs() ? isVarargAssignable(left, right) : TypeConversionUtil.isAssignable(declarationType, valueType), true);
        }
        break;

      case TypeInfection.RIGHT_INFECTED:
        PsiType psiType = migrationType != null ? migrationType : right.getType();
        if (psiType != null) {
          if (canBeVariableType(psiType)) {
            if (declarationType != null &&
                !myLabeler.addMigrationRoot(variable, psiType, myStatement, TypeConversionUtil.isAssignable(declarationType, psiType), true) &&
                !TypeConversionUtil.isAssignable(left.getType(), psiType)) {
              PsiType initialType = left.getType();
              if (initialType instanceof PsiEllipsisType) {
                initialType = ((PsiEllipsisType)initialType).getComponentType();
              }
              myLabeler.convertExpression(value, psiType, initialType, isCovariantPosition);
            }
          }
          else {
            if (variable instanceof PsiLocalVariable) {
              final PsiDeclarationStatement decl = PsiTreeUtil.getParentOfType(variable, PsiDeclarationStatement.class);
              if (decl != null && decl.getDeclaredElements().length == 1) {
                tryToRemoveLocalVariableAssignment((PsiLocalVariable)variable, value, psiType);
              }
              break;
            }
          }
        }
        break;

      case TypeInfection.BOTH_INFECTED:
        addTypeUsage(variable);
        break;

      default:
        LOG.error("Must not happen.");
    }
  }

  private void tryToRemoveLocalVariableAssignment(@NotNull PsiLocalVariable variable, @NotNull PsiExpression valueExpression, @NotNull PsiType migrationType) {
    final PsiCodeBlock codeBlock = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
    final PsiElement[] refs = DefUseUtil.getRefs(codeBlock, variable, valueExpression);
    if (refs.length == 0) {
      myLabeler.setConversionMapping(valueExpression, new TypeConversionDescriptorBase() {
        @Override
        public PsiExpression replace(PsiExpression expression, @NotNull TypeEvaluator evaluator) throws IncorrectOperationException {
          final PsiElement parent = expression.getParent();
          if (parent instanceof PsiLocalVariable) {
            final PsiLocalVariable var = (PsiLocalVariable)parent;
            final PsiDeclarationStatement decl = PsiTreeUtil.getParentOfType(var, PsiDeclarationStatement.class);
            if (decl == null) return null;
            final Project project = var.getProject();
            final PsiAssignmentExpression assignment =
              SplitDeclarationAction.invokeOnDeclarationStatement(decl, PsiManager.getInstance(project), project);
            final PsiExpression rExpression = assignment.getRExpression();
            if (rExpression == null) return null;
            assignment.replace(rExpression);
            if (ReferencesSearch.search(var).forEach(new CommonProcessors.FindFirstProcessor<>())) {
              var.delete();
            }
          }
          else if (parent instanceof PsiAssignmentExpression) {
            final PsiExpression rExpression = ((PsiAssignmentExpression)parent).getRExpression();
            return rExpression == null ? null : (PsiExpression)parent.replace(rExpression);
          }
          return null;
        }
      });
    } else {
      myLabeler.markFailedConversion(Pair.pair(null, migrationType), valueExpression);
    }
  }


  private static boolean canBeVariableType(@NotNull PsiType type) {
    return !type.getDeepComponentType().equals(PsiType.VOID);
  }

  private static PsiType adjustMigrationTypeIfGenericArrayCreation(PsiType migrationType, PsiExpression expression) {
    if (expression instanceof PsiNewExpression) {
      if (migrationType instanceof PsiArrayType) {
        final PsiType componentType = migrationType.getDeepComponentType();
        if (componentType instanceof PsiClassType) {
          final PsiClassType rawType = ((PsiClassType)componentType).rawType();
          if (!rawType.equals(componentType)) {
            return com.intellij.refactoring.typeCook.Util.createArrayType(rawType, migrationType.getArrayDimensions());
          }
        }
      }
    }
    return migrationType;
  }


  private void addTypeUsage(final PsiElement typedElement) {
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

    public TypeView(@NotNull PsiExpression expr) {
      PsiType exprType = expr.getType();
      exprType = exprType instanceof PsiEllipsisType ? ((PsiEllipsisType)exprType).toArrayType() : exprType;
      myOriginType = GenericsUtil.getVariableTypeByExpressionType(exprType);
      PsiType type = myTypeEvaluator.evaluateType(expr);
      type = type instanceof PsiEllipsisType ? ((PsiEllipsisType)type).toArrayType() : type;
      myType = GenericsUtil.getVariableTypeByExpressionType(type);
      myChanged = !(myOriginType == null || myType == null) && !myType.equals(myOriginType);
    }

    public TypeView(PsiVariable var, PsiSubstitutor varSubstitutor, PsiSubstitutor evalSubstitutor) {
      myOriginType = varSubstitutor != null ? varSubstitutor.substitute(var.getType()) : var.getType();

      Map<PsiTypeParameter, PsiType> realMap = new HashMap<>();
      if (varSubstitutor != null) realMap.putAll(varSubstitutor.getSubstitutionMap());
      if (evalSubstitutor != null) realMap.putAll(evalSubstitutor.getSubstitutionMap());

      myType = PsiSubstitutorImpl.createSubstitutor(realMap).substitute(myTypeEvaluator.getType(var));
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

  private static class TypeInfection {
    static final int NONE_INFECTED = 0;
    static final int LEFT_INFECTED = 1;
    static final int RIGHT_INFECTED = 2;
    static final int BOTH_INFECTED = 3;

    static int getInfection(final TypeView left, final TypeView right) {
      return (left.isChanged() ? 1 : 0) + (right.isChanged() ? 2 : 0);
    }
  }

  private static boolean isSetter(PsiAssignmentExpression expression) {
    final PsiExpression lExpression = expression.getLExpression();
    if (lExpression instanceof PsiReferenceExpression) {
      final PsiElement resolved = ((PsiReferenceExpression)lExpression).resolve();
      if (resolved instanceof PsiField) {
        PsiField field = (PsiField) resolved;
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
      if (resolved instanceof PsiField) {
        PsiField field = (PsiField)resolved;
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
    PsiSubstitutor result = PsiSubstitutorImpl.createSubstitutor(actual.getSubstitutionMap());
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
