// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.util.duplicates;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class Match {
  private static final Logger LOG = Logger.getInstance(Match.class);
  private final PsiElement myMatchStart;
  private final PsiElement myMatchEnd;
  private final Map<PsiVariable, List<PsiElement>> myParameterValues = new HashMap<>();
  private final Map<PsiVariable, List<PsiElement>> myParameterOccurrences = new HashMap<>();
  private final Map<PsiElement, PsiElement> myDeclarationCorrespondence = new HashMap<>();
  private ReturnValue myReturnValue;
  private Ref<PsiExpression> myInstanceExpression;
  final Map<PsiVariable, PsiType> myChangedParams = new HashMap<>();
  private final boolean myIgnoreParameterTypes;
  private final List<ExtractedParameter> myExtractedParameters = new ArrayList<>();
  private final Map<DuplicatesFinder.Parameter, List<Pair.NonNull<PsiExpression, PsiExpression>>> myFoldedExpressionMappings = new HashMap<>();

  Match(PsiElement start, PsiElement end, boolean ignoreParameterTypes) {
    LOG.assertTrue(start.getParent() == end.getParent());
    myMatchStart = start;
    myMatchEnd = end;
    myIgnoreParameterTypes = ignoreParameterTypes;
  }


  public PsiElement getMatchStart() {
    return myMatchStart;
  }

  public PsiElement getMatchEnd() {
    return myMatchEnd;
  }

  public PsiElement[] getMatchElements() {
    return StreamEx.iterate(myMatchStart,
                            Objects::nonNull,
                            element -> element != myMatchEnd ? element.getNextSibling() : null)
                   .toArray(PsiElement.EMPTY_ARRAY);
  }

  @Nullable
  public List<PsiElement> getParameterValues(PsiVariable parameter) {
    return myParameterValues.get(parameter);
  }

  /**
   * Returns either local variable declaration or expression
   */
  public ReturnValue getOutputVariableValue(PsiVariable outputParameter) {
    final PsiElement decl = myDeclarationCorrespondence.get(outputParameter);
    if (decl instanceof PsiVariable) {
      return new VariableReturnValue((PsiVariable)decl);
    }
    final List<PsiElement> parameterValue = getParameterValues(outputParameter);
    if (parameterValue != null && parameterValue.size() == 1 && parameterValue.get(0) instanceof PsiExpression) {
      return new ExpressionReturnValue((PsiExpression) parameterValue.get(0));
    }
    else {
      return null;
    }
  }


  public boolean putParameter(DuplicatesFinder.Parameter parameter, PsiElement value) {
    final PsiVariable psiVariable = parameter.getVariable();

    if (myDeclarationCorrespondence.get(psiVariable) == null) {
      final boolean [] valueDependsOnReplacedScope = new boolean[1];
      value.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitReferenceExpression(final @NotNull PsiReferenceExpression expression) {
          super.visitReferenceExpression(expression);
          final PsiElement resolved = expression.resolve();
          if (resolved != null && Comparing.equal(resolved.getContainingFile(), getMatchEnd().getContainingFile())) {
            final TextRange range = checkRange(resolved);
            final TextRange startRange = checkRange(getMatchStart());
            final TextRange endRange = checkRange(getMatchEnd());
            if (startRange.getStartOffset() <= range.getStartOffset() && range.getEndOffset() <= endRange.getEndOffset()) {
              valueDependsOnReplacedScope[0] = true;
            }
          }
        }
      });
      if (valueDependsOnReplacedScope[0]) return false;
    }

    final List<PsiElement> currentValue = myParameterValues.get(psiVariable);
    final boolean isVararg = psiVariable instanceof PsiParameter && ((PsiParameter)psiVariable).isVarArgs();
    if (!(value instanceof PsiExpression)) return false;
    final PsiElement parent = value.getParent();
    if (parent instanceof PsiMethodCallExpression && value == ((PsiMethodCallExpression)parent).getMethodExpression()) return false;
    final PsiType type = ((PsiExpression)value).getType();
    final PsiType parameterType = parameter.getType();
    if (type == null) return false;
    if (currentValue == null) {
      if (PsiUtil.resolveClassInClassTypeOnly(parameterType) instanceof PsiTypeParameter typeParameter) {
        LOG.assertTrue(typeParameter != null);
        for (PsiClassType classType : typeParameter.getExtendsListTypes()) {
          if (!classType.isAssignableFrom(type)) return false;
        }
      }
      else {
        if (isVararg) {
          if (!((PsiEllipsisType)psiVariable.getType()).getComponentType().isAssignableFrom(type) && !((PsiEllipsisType)psiVariable.getType()).toArrayType().equals(type)) {
            myChangedParams.put(psiVariable, new PsiEllipsisType(parameterType));
          }
        } else {
          if (!myIgnoreParameterTypes && !parameterType.isAssignableFrom(type)) return false;  //todo
        }
      }
      final List<PsiElement> values = new ArrayList<>();
      values.add(value);
      myParameterValues.put(psiVariable, values);
      final ArrayList<PsiElement> elements = new ArrayList<>();
      myParameterOccurrences.put(psiVariable, elements);
    }
    else {
      for (PsiElement val : currentValue) {
        if (!isVararg && !PsiEquivalenceUtil.areElementsEquivalent(val, value)) {
          return false;
        }
      }
      if (isVararg) {
        if (!parameterType.isAssignableFrom(type)) return false;
        if (!((PsiEllipsisType)psiVariable.getType()).toArrayType().equals(type)){
          currentValue.add(value);
        }
      }
      myParameterOccurrences.get(psiVariable).add(value);
    }
    return true;
  }

  public ReturnValue getReturnValue() {
    return myReturnValue;
  }

  boolean registerReturnValue(ReturnValue returnValue) {
    if (myReturnValue == null) {
      myReturnValue = returnValue;
      return true;
    }
    else {
      return myReturnValue.isEquivalent(returnValue);
    }
  }

  boolean registerInstanceExpression(PsiExpression instanceExpression, final PsiClass contextClass) {
    if (myInstanceExpression == null) {
      if (instanceExpression != null) {
        final PsiType type = instanceExpression.getType();
        if (!(type instanceof PsiClassType)) return false;
        final PsiClass hisClass = ((PsiClassType) type).resolve();
        if (hisClass == null || !InheritanceUtil.isInheritorOrSelf(hisClass, contextClass, true)) return false;
      }
      myInstanceExpression = Ref.create(instanceExpression);
      return true;
    }
    else {
      if (myInstanceExpression.get() == null) {
        myInstanceExpression.set(instanceExpression);

        return instanceExpression == null;
      }
      if (instanceExpression != null) {
        return PsiEquivalenceUtil.areElementsEquivalent(instanceExpression, myInstanceExpression.get());
      }
      return myInstanceExpression.get() instanceof PsiThisExpression;
    }
  }

  boolean putDeclarationCorrespondence(PsiElement patternDeclaration, @NotNull PsiElement matchDeclaration) {
    PsiElement originalValue = myDeclarationCorrespondence.get(patternDeclaration);
    if (originalValue == null) {
      myDeclarationCorrespondence.put(patternDeclaration, matchDeclaration);
      return true;
    }
    else {
      return originalValue == matchDeclaration;
    }
  }

  boolean areCorrespond(PsiElement patternDeclaration, PsiElement matchDeclaration) {
    if (matchDeclaration == null || patternDeclaration == null) return false;
    PsiElement originalValue = myDeclarationCorrespondence.get(patternDeclaration);
    return originalValue == null || originalValue == matchDeclaration;
  }

  private PsiElement replaceWith(final PsiStatement statement) throws IncorrectOperationException {
    final PsiElement matchStart = getMatchStart();
    final PsiElement matchEnd = getMatchEnd();
    final PsiElement element = matchStart.getParent().addBefore(statement, matchStart);
    matchStart.getParent().deleteChildRange(matchStart, matchEnd);
    return element;
  }

  public PsiElement replaceByStatement(final PsiMethod extractedMethod, final PsiMethodCallExpression methodCallExpression, final PsiVariable outputVariable, @Nullable PsiType returnType) throws IncorrectOperationException {
    PsiStatement statement = null;
    if (outputVariable != null) {
      ReturnValue returnValue = getOutputVariableValue(outputVariable);
      if (returnValue == null && outputVariable instanceof PsiField) {
        returnValue = new FieldReturnValue((PsiField)outputVariable);
      }
      if (returnValue == null) return null;
      statement = returnValue.createReplacement(extractedMethod, methodCallExpression, returnType);
    }
    else if (getReturnValue() != null) {
      statement = getReturnValue().createReplacement(extractedMethod, methodCallExpression, returnType);
    }
    if (statement == null) {
      final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(methodCallExpression.getProject());
      PsiExpressionStatement expressionStatement = (PsiExpressionStatement) elementFactory.createStatementFromText("x();", null);
      final CodeStyleManager styleManager = CodeStyleManager.getInstance(methodCallExpression.getManager());
      expressionStatement = (PsiExpressionStatement)styleManager.reformat(expressionStatement);
      expressionStatement.getExpression().replace(methodCallExpression);
      statement = expressionStatement;
    }
    return replaceWith(statement);
  }

  public PsiExpression getInstanceExpression() {
    if (myInstanceExpression == null) {
      return null;
    }
    else {
      return myInstanceExpression.get();
    }
  }

  public PsiElement replace(final PsiMethod extractedMethod, final PsiMethodCallExpression methodCallExpression, PsiVariable outputVariable) throws IncorrectOperationException {
    return replace(extractedMethod, methodCallExpression, outputVariable, null);
  }

  public PsiElement replace(final PsiMethod extractedMethod, final PsiMethodCallExpression methodCallExpression, PsiVariable outputVariable, @Nullable PsiType returnType) throws IncorrectOperationException {
    declareLocalVariables();
    if (getMatchStart() == getMatchEnd() && getMatchStart() instanceof PsiExpression) {
      return replaceWithExpression(methodCallExpression);
    }
    else {
      return replaceByStatement(extractedMethod, methodCallExpression, outputVariable, returnType);
    }
  }

  private void declareLocalVariables() throws IncorrectOperationException {
    final PsiElement codeFragment = ControlFlowUtil.findCodeFragment(getMatchStart());
    try {
      final Project project = getMatchStart().getProject();
      final ControlFlow controlFlow = ControlFlowFactory.getControlFlow(codeFragment, new LocalsControlFlowPolicy(codeFragment), 
                                                                        ControlFlowOptions.NO_CONST_EVALUATE);
      final int endOffset = controlFlow.getEndOffset(getMatchEnd());
      final int startOffset = controlFlow.getStartOffset(getMatchStart());
      final List<PsiVariable> usedVariables = ControlFlowUtil.getUsedVariables(controlFlow, endOffset, controlFlow.getSize());
      Collection<ControlFlowUtil.VariableInfo> reassigned = ControlFlowUtil.getInitializedTwice(controlFlow, endOffset, controlFlow.getSize());
      final Collection<PsiVariable> outVariables = ControlFlowUtil.getWrittenVariables(controlFlow, startOffset, endOffset, false);
      for (PsiVariable variable : usedVariables) {
        if (!outVariables.contains(variable)) {
          final PsiIdentifier identifier = variable.getNameIdentifier();
          if (identifier != null) {
            final TextRange textRange = checkRange(identifier);
            final TextRange startRange = checkRange(getMatchStart());
            final TextRange endRange = checkRange(getMatchEnd());
            if (textRange.getStartOffset() >= startRange.getStartOffset() && textRange.getEndOffset() <= endRange.getEndOffset()) {
              final String name = variable.getName();
              LOG.assertTrue(name != null);
              PsiDeclarationStatement statement =
                  JavaPsiFacade.getElementFactory(project).createVariableDeclarationStatement(name, variable.getType(), null);
              if (reassigned.contains(new ControlFlowUtil.VariableInfo(variable, null))) {
                final PsiElement[] psiElements = statement.getDeclaredElements();
                final PsiModifierList modifierList = ((PsiVariable)psiElements[0]).getModifierList();
                LOG.assertTrue(modifierList != null);
                modifierList.setModifierProperty(PsiModifier.FINAL, false);
              }
              getMatchStart().getParent().addBefore(statement, getMatchStart());
            }
          }
        }
      }
    }
    catch (AnalysisCanceledException e) {
      //skip match
    }
  }

  private static TextRange checkRange(final PsiElement element) {
    final TextRange endRange = element.getTextRange();
    LOG.assertTrue(endRange != null, element);
    return endRange;
  }

  public PsiElement replaceWithExpression(final PsiExpression psiExpression) throws IncorrectOperationException {
    final PsiElement matchStart = getMatchStart();
    LOG.assertTrue(matchStart == getMatchEnd());
    if (psiExpression instanceof PsiMethodCallExpression && matchStart instanceof PsiReferenceExpression && matchStart.getParent() instanceof PsiMethodCallExpression) {
      return JavaCodeStyleManager.getInstance(matchStart.getProject()).shortenClassReferences(matchStart.replace(((PsiMethodCallExpression)psiExpression).getMethodExpression()));
    }
    return JavaCodeStyleManager.getInstance(matchStart.getProject()).shortenClassReferences(matchStart.replace(psiExpression));
  }

  TextRange getTextRange() {
    final TextRange startRange = checkRange(getMatchStart());
    final TextRange endRange = checkRange(getMatchEnd());
    return new TextRange(startRange.getStartOffset(), endRange.getEndOffset());
  }

  @Nullable
  public PsiType getChangedReturnType(final PsiMethod psiMethod) {
    final PsiType returnType = psiMethod.getReturnType();
    if (returnType != null) {
      PsiElement parent = getMatchEnd().getParent();

      if (parent instanceof PsiExpression) {
        if (parent instanceof PsiMethodCallExpression) {
          JavaResolveResult result = ((PsiMethodCallExpression)parent).resolveMethodGenerics();
          final PsiMethod method = (PsiMethod)result.getElement();
          if (method != null) {
            PsiType type = method.getReturnType();
            if (type != null) {
              type = result.getSubstitutor().substitute(type);
              if (weakerType(psiMethod, returnType, type)) {
                return type;
              }
            }
          }
        }
        else if (parent instanceof PsiReferenceExpression) {
          final JavaResolveResult result = ((PsiReferenceExpression)parent).advancedResolve(false);
          final PsiElement element = result.getElement();
          if (element instanceof PsiMember) {
            final PsiClass psiClass = ((PsiMember)element).getContainingClass();
            if (psiClass != null && psiClass.isPhysical()) {
              final JavaPsiFacade facade = JavaPsiFacade.getInstance(parent.getProject());
              final PsiClassType expressionType = facade.getElementFactory().createType(psiClass, result.getSubstitutor());
              if (weakerType(psiMethod, returnType, expressionType)) {
                return expressionType;
              }
            }
          }
        }
      }
      else if (parent instanceof PsiExpressionList) {
        final PsiExpression[] expressions = ((PsiExpressionList)parent).getExpressions();
        final PsiElement call = parent.getParent();
        if (call instanceof PsiMethodCallExpression) {
          final JavaResolveResult result = ((PsiMethodCallExpression)call).resolveMethodGenerics();
          final PsiMethod method = (PsiMethod)result.getElement();
          if (method != null) {
            final int idx = ArrayUtil.find(expressions, getMatchEnd());
            final PsiParameter[] psiParameters = method.getParameterList().getParameters();
            if (idx >= 0 && idx < psiParameters.length) {
              PsiType type = result.getSubstitutor().substitute(psiParameters[idx].getType());
              if (type instanceof PsiEllipsisType) {
                type = ((PsiEllipsisType)type).getComponentType();
              }
              if (weakerType(psiMethod, returnType, type)){
                return type;
              }
            }
          }
        }
      }
      else if (parent instanceof PsiLocalVariable) {
        final PsiType localVariableType = ((PsiLocalVariable)parent).getType();
        if (weakerType(psiMethod, returnType, localVariableType)) return localVariableType;
      }
      else if (parent instanceof PsiReturnStatement) {
        final PsiMethod replacedMethod = PsiTreeUtil.getParentOfType(parent, PsiMethod.class);
        LOG.assertTrue(replacedMethod != null);
        final PsiType replacedMethodReturnType = replacedMethod.getReturnType();
        if (replacedMethodReturnType != null && weakerType(psiMethod, returnType, replacedMethodReturnType)) {
          return replacedMethodReturnType;
        }
      }

    }
    return null;
  }

  private static boolean weakerType(final PsiMethod psiMethod, final PsiType returnType, @NotNull final PsiType currentType) {
    final PsiTypeParameter[] typeParameters = psiMethod.getTypeParameters();
    final PsiSubstitutor substitutor =
        JavaPsiFacade.getInstance(psiMethod.getProject()).getResolveHelper().inferTypeArguments(typeParameters, new PsiType[]{returnType}, new PsiType[]{currentType}, PsiUtil.getLanguageLevel(psiMethod));

    return !TypeConversionUtil.isAssignable(currentType, substitutor.substitute(returnType));
  }

  public PsiFile getFile() {
    return getMatchStart().getContainingFile();
  }

  public boolean putExtractedParameter(@NotNull ExtractableExpressionPart patternPart, @NotNull ExtractableExpressionPart candidatePart) {
    return ExtractedParameter.match(patternPart, candidatePart, myExtractedParameters);
  }

  public void addExtractedParameter(@NotNull ExtractedParameter parameter) {
    myExtractedParameters.add(parameter);
  }

  @NotNull
  public List<ExtractedParameter> getExtractedParameters() {
    return myExtractedParameters;
  }

  public void putFoldedExpressionMapping(@NotNull DuplicatesFinder.Parameter parameter,
                                         @NotNull PsiExpression pattern,
                                         @NotNull PsiExpression candidate) {
    myFoldedExpressionMappings.computeIfAbsent(parameter, unused -> new ArrayList<>()).add(Pair.createNonNull(pattern, candidate));
  }

  @Nullable
  public List<Pair.NonNull<PsiExpression, PsiExpression>> getFoldedExpressionMappings(@NotNull DuplicatesFinder.Parameter parameter) {
    return myFoldedExpressionMappings.get(parameter);
  }
}
