/*
 * User: anna
 * Date: 04-Apr-2008
 */
package com.intellij.refactoring.typeMigration;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.typeMigration.usageInfo.TypeMigrationUsageInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;

class TypeMigrationStatementProcessor extends JavaRecursiveElementVisitor {
  private final PsiElement myStatement;
  private final TypeMigrationLabeler myLabeler;
  private static final Logger LOG = Logger.getInstance("#" + TypeMigrationStatementProcessor.class.getName());
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
    if (sign != JavaTokenType.EQ) {
      final IElementType binaryOperator = TypeConversionUtil.convertEQtoOperation(sign);
      if (!TypeConversionUtil.isBinaryOperatorApplicable(binaryOperator, left.getType(), right.getType(), false)) {
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
        myLabeler.migrateExpressionType(rExpression, left.getType(), myStatement, TypeConversionUtil.isAssignable(left.getType(), right.getType()), true);
        break;

      case TypeInfection.RIGHT_INFECTED:
        myLabeler.migrateExpressionType(lExpression, right.getType(), myStatement, TypeConversionUtil.isAssignable(left.getType(), right.getType()), false);
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
        myLabeler.markFailedConversion(new Pair<PsiType, PsiType>(fixedType, migrationType), consideredExpression);
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
        myLabeler.markFailedConversion(new Pair<PsiType, PsiType>(fixedType, migrationType), expression);
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
  public void visitReturnStatement(PsiReturnStatement statement) { // has to change method return type corresponding to new value type 
    super.visitReturnStatement(statement);

    final PsiMethod method = PsiTreeUtil.getParentOfType(statement, PsiMethod.class);
    final PsiExpression value = statement.getReturnValue();

    if (method != null && value != null) {
      final PsiType returnType = method.getReturnType();
      final PsiType valueType = myTypeEvaluator.evaluateType(value);

      if (returnType != null && valueType != null) {
        if (!myLabeler.addMigrationRoot(method, valueType, myStatement, TypeConversionUtil.isAssignable(returnType, valueType), true)
            && TypeMigrationLabeler.typeContainsTypeParameters(returnType)) {
          myLabeler.markFailedConversion(new Pair<PsiType, PsiType>(returnType, valueType), value);
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
          if (parent instanceof PsiMethodCallExpression) {
            myLabeler.setConversionMapping((PsiMethodCallExpression)parent, conversion);
            myTypeEvaluator.setType(new TypeMigrationUsageInfo(parent), myTypeEvaluator.evaluateType((PsiExpression)parent));
          } else {
            myLabeler.setConversionMapping(expression, conversion);
            myTypeEvaluator.setType(new TypeMigrationUsageInfo(expression), myTypeEvaluator.evaluateType(expression));
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
        final Project project = statement.getProject();
        final PsiClass iterableClass =
            JavaPsiFacade.getInstance(project).findClass("java.lang.Iterable", GlobalSearchScope.allScope(project));
        if (iterableClass == null) return;
        if (!InheritanceUtil.isInheritorOrSelf(psiClass, iterableClass, true)) {
          findConversionOrFail(value, value, typeView.getTypePair());
          return;
        }
        final PsiSubstitutor iterableParamSubstitutor =
            TypeConversionUtil.getClassSubstitutor(iterableClass, psiClass, PsiSubstitutor.EMPTY);
        LOG.assertTrue(iterableParamSubstitutor != null);
        final PsiTypeParameter[] typeParameters = iterableClass.getTypeParameters();
        LOG.assertTrue(typeParameters.length == 1);
        psiType = resolveResult.getSubstitutor().substitute(iterableParamSubstitutor.substitute(typeParameters[0]));
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
          final PsiTypeParameter[] typeParameters = iterableClass.getTypeParameters();
          LOG.assertTrue(typeParameters.length == 1);
          final Map<PsiTypeParameter, PsiType> substMap = Collections.singletonMap(typeParameters[0], left.getType());
          final PsiElementFactory factory = JavaPsiFacade.getElementFactory(iterableClass.getProject());
          iterableType = factory.createType(iterableClass, factory.createSubstitutor(substMap));
        }
        myLabeler.migrateExpressionType(value, iterableType, myStatement, TypeConversionUtil.isAssignable(iterableType, typeViewType), true);
      } else {
        processVariable(psiParameter, value, psiType, null, null, false);
      }
    }
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
  public void visitPostfixExpression(final PsiPostfixExpression expression) {
    super.visitPostfixExpression(expression);
    processUnaryExpression(expression, expression.getOperationSign());
  }

  @Override
  public void visitPrefixExpression(final PsiPrefixExpression expression) {
    super.visitPrefixExpression(expression);
    processUnaryExpression(expression, expression.getOperationSign());
  }

  private void processUnaryExpression(final PsiExpression expression, PsiJavaToken sign) {
    final TypeView typeView = new TypeView(expression);
    if (typeView.isChanged()) {
      if (!TypeConversionUtil.isUnaryOperatorApplicable(sign, typeView.getType())) {
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
      myTypeEvaluator.setType(new TypeMigrationUsageInfo(expression), myTypeEvaluator.evaluateType(expression));
    }
  }

  @Override
  public void visitBinaryExpression(final PsiBinaryExpression expression) {
    super.visitBinaryExpression(expression);
    final PsiExpression lOperand = expression.getLOperand();
    final TypeView left = new TypeView(lOperand);
    final PsiExpression rOperand = expression.getROperand();
    if (rOperand == null) return;
    final TypeView right = new TypeView(rOperand);
    if (!TypeConversionUtil.isBinaryOperatorApplicable(expression.getOperationSign().getTokenType(), left.getType(), right.getType(), false)) {
      if (left.isChanged()) {
        findConversionOrFail(expression, lOperand, left.getTypePair());
      }
      if (right.isChanged()) {
        findConversionOrFail(expression, rOperand, right.getTypePair());
      }
    }
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
            myLabeler.markFailedConversion(new Pair<PsiType, PsiType>(parentExpression.getType(), type), parentExpression);
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
      myLabeler.markFailedConversion(new Pair<PsiType, PsiType>(indexExpression.getType(), indexType), indexExpression);
    }
  }

  @Override
  public void visitMethodCallExpression(final PsiMethodCallExpression methodCallExpression) {
    super.visitMethodCallExpression(methodCallExpression);
    final JavaResolveResult resolveResult = methodCallExpression.resolveMethodGenerics();
    final PsiElement method = resolveResult.getElement();
    if (method instanceof PsiMethod) {
      final PsiExpression[] psiExpressions = methodCallExpression.getArgumentList().getExpressions();
      final PsiParameter[] originalParams = ((PsiMethod)method).getParameterList().getParameters();
      final PsiSubstitutor evalSubstitutor = myTypeEvaluator.createMethodSubstitution(originalParams, psiExpressions, (PsiMethod)method, methodCallExpression);
      for (int i = 0; i < psiExpressions.length; i++) {
        PsiParameter originalParameter;
        if (originalParams.length <= i) {
          if (originalParams[originalParams.length - 1].isVarArgs()) {
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
          final PsiType migrationType =
              classResolveResult.getSubstitutor().substitute(evalSubstitutor.substitute(JavaPsiFacade.getElementFactory(myStatement.getProject()).createType(classResolveResult.getElement(), PsiSubstitutor.EMPTY)));
          myLabeler.migrateExpressionType(qualifier, migrationType, myStatement, migrationType.equals(qualifierType), true);
        }
      }
    }
  }

  private void processVariable(final PsiVariable variable,
                               final PsiExpression value,
                               final PsiType migrationType,
                               final PsiSubstitutor varSubstitutor,
                               final PsiSubstitutor evalSubstitutor,
                               final boolean isCovariantPosition) {
    final TypeView right = new TypeView(value);
    final TypeView left = new TypeView(variable, varSubstitutor, evalSubstitutor);

    switch (TypeInfection.getInfection(left, right)) {
      case TypeInfection.NONE_INFECTED:
        break;

      case TypeInfection.LEFT_INFECTED:
        myLabeler.migrateExpressionType(value, left.getType(), myStatement, TypeConversionUtil.isAssignable(left.getType(), right.getType()), true);
        break;

      case TypeInfection.RIGHT_INFECTED:
        PsiType psiType = migrationType != null ? migrationType : right.getType();
        if (!myLabeler.addMigrationRoot(variable, psiType, myStatement, TypeConversionUtil.isAssignable(left.getType(), psiType), true)) {
          myLabeler.convertExpression(value, psiType, left.getType(), isCovariantPosition);
        }
        break;

      case TypeInfection.BOTH_INFECTED:
        addTypeUsage(variable);
        break;

      default:
        LOG.error("Must not happen.");
    }
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
      myOriginType = exprType != null ? GenericsUtil.getVariableTypeByExpressionType(exprType) : null;
      PsiType type = myTypeEvaluator.evaluateType(expr);
      type = type instanceof PsiEllipsisType ? ((PsiEllipsisType)type).toArrayType() : type;
      myType = type != null ? GenericsUtil.getVariableTypeByExpressionType(type) : null;
      myChanged = (myOriginType == null || myType == null) ? false : !myType.equals(myOriginType);
    }

    public TypeView(PsiVariable var, PsiSubstitutor varSubstitutor, PsiSubstitutor evalSubstitutor) {
      myOriginType = varSubstitutor != null ? varSubstitutor.substitute(var.getType()) : var.getType();
      myType = evalSubstitutor != null
               ? evalSubstitutor.substitute(myTypeEvaluator.getType(var))
               : myTypeEvaluator.getType(var);
      myChanged = (myOriginType == null || myType == null) ? false : !myType.equals(myOriginType);
    }

    public PsiType getType() {
      return myType;
    }

    public boolean isChanged() {
      return myChanged;
    }

    public Pair<PsiType, PsiType> getTypePair() {
      return new Pair<PsiType, PsiType>(myOriginType, myType);
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
}