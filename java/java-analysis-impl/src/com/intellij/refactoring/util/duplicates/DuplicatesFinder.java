/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.refactoring.util.duplicates;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.intellij.refactoring.extractMethod.InputVariables;
import com.intellij.refactoring.util.RefactoringChangeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.IntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author dsl
 */
public class DuplicatesFinder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.util.duplicates.DuplicatesFinder");
  public static final Key<Pair<PsiVariable, PsiType>> PARAMETER = Key.create("PARAMETER");
  @NotNull private final PsiElement[] myPattern;
  private final InputVariables myParameters;
  private final List<? extends PsiVariable> myOutputParameters;
  private final List<PsiElement> myPatternAsList;
  private boolean myMultipleExitPoints;
  @Nullable private final ReturnValue myReturnValue;
  private final boolean myWithExtractedParameters;
  private final Set<PsiVariable> myEffectivelyLocal;
  private ComplexityHolder myPatternComplexityHolder;
  private ComplexityHolder myCandidateComplexityHolder;

  public DuplicatesFinder(@NotNull PsiElement[] pattern,
                          InputVariables parameters,
                          @Nullable ReturnValue returnValue,
                          @NotNull List<? extends PsiVariable> outputParameters,
                          boolean withExtractedParameters,
                          @Nullable Set<PsiVariable> effectivelyLocal) {
    myReturnValue = returnValue;
    LOG.assertTrue(pattern.length > 0);
    myPattern = pattern;
    myPatternAsList = Arrays.asList(myPattern);
    myParameters = parameters;
    myOutputParameters = outputParameters;
    myWithExtractedParameters = withExtractedParameters;
    myEffectivelyLocal = effectivelyLocal != null ? effectivelyLocal : Collections.emptySet();

    final PsiElement codeFragment = ControlFlowUtil.findCodeFragment(pattern[0]);
    try {
      final ControlFlow controlFlow = ControlFlowFactory.getInstance(codeFragment.getProject()).getControlFlow(codeFragment, new LocalsControlFlowPolicy(codeFragment), false);

      int startOffset;
      int i = 0;
      do {
        startOffset = controlFlow.getStartOffset(pattern[i++]);
      } while(startOffset < 0 && i < pattern.length);

      int endOffset;
      int j = pattern.length - 1;
      do {
        endOffset = controlFlow.getEndOffset(pattern[j--]);
      } while(endOffset < 0 && j >= 0);

      IntArrayList exitPoints = new IntArrayList();
      final Collection<PsiStatement> exitStatements = ControlFlowUtil
          .findExitPointsAndStatements(controlFlow, startOffset, endOffset, exitPoints, ControlFlowUtil.DEFAULT_EXIT_STATEMENTS_CLASSES);
      myMultipleExitPoints = exitPoints.size() > 1;

      if (myMultipleExitPoints) {
        myParameters.removeParametersUsedInExitsOnly(codeFragment, exitStatements, controlFlow, startOffset, endOffset);
      }
    }
    catch (AnalysisCanceledException e) {
    }
  }

  public DuplicatesFinder(@NotNull PsiElement[] pattern,
                          InputVariables parameters,
                          @Nullable ReturnValue returnValue,
                          @NotNull List<? extends PsiVariable> outputParameters) {
    this(pattern, parameters, returnValue, outputParameters, false, null);
  }

  public DuplicatesFinder(final PsiElement[] pattern,
                          final InputVariables psiParameters,
                          final List<? extends PsiVariable> psiVariables) {
    this(pattern, psiParameters, null, psiVariables);
  }


  public InputVariables getParameters() {
    return myParameters;
  }

  @NotNull
  public PsiElement[] getPattern() {
    return myPattern;
  }

  @Nullable
  public ReturnValue getReturnValue() {
    return myReturnValue;
  }

  public List<Match> findDuplicates(PsiElement scope) {
    annotatePattern();
    final ArrayList<Match> result = new ArrayList<>();
    findPatternOccurrences(result, scope);
    deannotatePattern();
    return result;
  }

  @Nullable
  public Match isDuplicate(@NotNull PsiElement element, boolean ignoreParameterTypesAndPostVariableUsages) {
    annotatePattern();
    Match match = isDuplicateFragment(element, ignoreParameterTypesAndPostVariableUsages);
    deannotatePattern();
    return match;
  }

  private void annotatePattern() {
    for (final PsiElement patternComponent : myPattern) {
      patternComponent.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
          final PsiElement element = reference.resolve();
          if (element instanceof PsiVariable) {
            final PsiVariable variable = (PsiVariable)element;
            PsiType type = variable.getType();
            myParameters.annotateWithParameter(reference);
            if (myOutputParameters.contains(element)) {
              reference.putUserData(PARAMETER, Pair.create(variable, type));
            }
          }
          PsiElement qualifier = reference.getQualifier();
          if (qualifier != null) {
            qualifier.accept(this);
          }
        }
      });
    }
  }

  private void deannotatePattern() {
    for (final PsiElement patternComponent : myPattern) {
      patternComponent.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
          if (reference.getUserData(PARAMETER) != null) {
            reference.putUserData(PARAMETER, null);
          }
        }
      });
    }
  }

  private void findPatternOccurrences(List<Match> array, PsiElement scope) {
    PsiElement[] children = scope.getChildren();
    for (PsiElement child : children) {
      final Match match = isDuplicateFragment(child, false);
      if (match != null) {
        array.add(match);
        continue;
      }
      findPatternOccurrences(array, child);
    }
  }


  @Nullable
  private Match isDuplicateFragment(@NotNull PsiElement candidate, boolean ignoreParameterTypesAndPostVariableUsages) {
    if (isSelf(candidate)) return null;
    PsiElement sibling = candidate;
    ArrayList<PsiElement> candidates = new ArrayList<>();
    for (final PsiElement element : myPattern) {
      if (sibling == null) return null;
      if (!canBeEquivalent(element, sibling) || isSelf(sibling)) return null;
      candidates.add(sibling);
      sibling = PsiTreeUtil.skipSiblingsForward(sibling, PsiWhiteSpace.class, PsiComment.class, PsiEmptyStatement.class);
    }
    LOG.assertTrue(myPattern.length == candidates.size());
    if (myPattern.length == 1 && myPattern[0] instanceof PsiExpression) {
      if (candidates.get(0) instanceof PsiExpression) {
        final PsiExpression candidateExpression = (PsiExpression)candidates.get(0);
        if (PsiUtil.isAccessedForWriting(candidateExpression)) return null;
        final PsiType patternType = ((PsiExpression)myPattern[0]).getType();
        final PsiType candidateType = candidateExpression.getType();
        PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
        final PsiMethod method = PsiTreeUtil.getContextOfType(myPattern[0], PsiMethod.class);
        if (method != null) {
          final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(candidate.getProject()).getResolveHelper();
          substitutor = resolveHelper.inferTypeArguments(method.getTypeParameters(), new PsiType[]{patternType},
                                                         new PsiType[]{candidateType}, PsiUtil.getLanguageLevel(method));
        }
        if (!canTypesBeEquivalent(substitutor.substitute(patternType), candidateType)) return null;
      }
      else {
        return null;
      }

    }
    final Match match = new Match(candidates.get(0), candidates.get(candidates.size() - 1), ignoreParameterTypesAndPostVariableUsages);
    for (int i = 0; i < myPattern.length; i++) {
      if (!matchPattern(myPattern[i], candidates.get(i), candidates, match)) return null;
    }

    if (!ignoreParameterTypesAndPostVariableUsages && checkPostVariableUsages(candidates, match)) return null;

    return match;
  }

  protected boolean isSelf(@NotNull PsiElement candidate) {
    for (PsiElement pattern : myPattern) {
      if (PsiTreeUtil.isAncestor(pattern, candidate, false)) {
        return true;
      }
    }
    return false;
  }

  private boolean checkPostVariableUsages(final ArrayList<PsiElement> candidates, final Match match) {
    final PsiElement codeFragment = ControlFlowUtil.findCodeFragment(candidates.get(0));
    try {
      final ControlFlow controlFlow = ControlFlowFactory.getInstance(codeFragment.getProject()).getControlFlow(codeFragment, new LocalsControlFlowPolicy(codeFragment), false);

      int startOffset;
      int i = 0;
      do {
        startOffset = controlFlow.getStartOffset(candidates.get(i++));
      } while(startOffset < 0 && i < candidates.size());

      int endOffset;
      int j = candidates.size() - 1;
      do {
        endOffset = controlFlow.getEndOffset(candidates.get(j--));
      } while(endOffset < 0 && j >= 0);

      final IntArrayList exitPoints = new IntArrayList();
      ControlFlowUtil.findExitPointsAndStatements(controlFlow, startOffset, endOffset, exitPoints, ControlFlowUtil.DEFAULT_EXIT_STATEMENTS_CLASSES);
      final PsiVariable[] outVariables = ControlFlowUtil.getOutputVariables(controlFlow, startOffset, endOffset, exitPoints.toArray());

      if (outVariables.length > 0) {
        if (outVariables.length == 1) {
          ReturnValue returnValue = match.getReturnValue();
          if (returnValue == null) {
            returnValue = myReturnValue;
          }
          if (returnValue instanceof GotoReturnValue ||
              returnValue instanceof ConditionalReturnStatementValue &&
              ((ConditionalReturnStatementValue)returnValue).isEmptyOrConstantExpression()) {
            return false;
          }
          if (returnValue instanceof VariableReturnValue) {
            final ReturnValue value = match.getOutputVariableValue(((VariableReturnValue)returnValue).getVariable());
            if (value != null) {
              if (value.isEquivalent(new VariableReturnValue(outVariables[0]))) return false;
              if (value instanceof ExpressionReturnValue) {
                final PsiExpression expression = ((ExpressionReturnValue)value).getExpression();
                if (expression instanceof PsiReferenceExpression) {
                  final PsiElement variable = ((PsiReferenceExpression)expression).resolve();
                  return variable == null || !PsiEquivalenceUtil.areElementsEquivalent(variable, outVariables[0]);
                }
              }
            }
          }
        }
        return true;
      }
    }
    catch (AnalysisCanceledException ignored) {
    }
    return false;
  }

  private static boolean canTypesBeEquivalent(PsiType type1, PsiType type2) {
    if (type1 == null || type2 == null) return false;
    if (!type2.isAssignableFrom(type1)) {
      if (type1 instanceof PsiImmediateClassType && type2 instanceof PsiImmediateClassType) {
        final PsiClass psiClass1 = ((PsiImmediateClassType)type1).resolve();
        final PsiClass psiClass2 = ((PsiImmediateClassType)type2).resolve();
        if (!(psiClass1 instanceof PsiAnonymousClass &&
              psiClass2 instanceof PsiAnonymousClass &&
              psiClass1.getManager().areElementsEquivalent(((PsiAnonymousClass)psiClass1).getBaseClassType().resolve(),
                                                           ((PsiAnonymousClass)psiClass2).getBaseClassType().resolve()))) {
          return false;
        }
      }
      else {
        return false;
      }
    }
    return true;
  }

  private static boolean canBeEquivalent(final PsiElement pattern, PsiElement candidate) {
    if (pattern instanceof PsiReturnStatement && candidate instanceof PsiExpressionStatement) return true;
    if (pattern instanceof PsiReturnStatement && candidate instanceof PsiDeclarationStatement) return true;
    if (pattern instanceof PsiThisExpression && candidate instanceof PsiReferenceExpression) return true;
    final ASTNode node1 = pattern.getNode();
    final ASTNode node2 = candidate.getNode();
    if (node1 == null || node2 == null) return false;
    if (node1.getElementType() != node2.getElementType()) return false;
    if (pattern instanceof PsiUnaryExpression) {
      return ((PsiUnaryExpression)pattern).getOperationTokenType() == ((PsiUnaryExpression)candidate).getOperationTokenType();
    }
    if (pattern instanceof PsiPolyadicExpression) {
      return ((PsiPolyadicExpression)pattern).getOperationTokenType() == ((PsiPolyadicExpression)candidate).getOperationTokenType();
    }
    return true;
  }

  private boolean matchPattern(PsiElement pattern,
                               PsiElement candidate,
                               List<PsiElement> candidates,
                               Match match) {
    if (pattern == null || candidate == null) return pattern == candidate;
    if (pattern.getUserData(PARAMETER) != null) {
      final Pair<PsiVariable, PsiType> parameter = pattern.getUserData(PARAMETER);
      if(!myWithExtractedParameters || parameter.second.equals(parameter.first.getType())) {
        return match.putParameter(parameter, candidate);
      }
    }

    if (!canBeEquivalent(pattern, candidate)) return false; // Q : is it correct to check implementation classes?

    if (pattern instanceof PsiExpressionList && candidate instanceof PsiExpressionList) { //check varargs
      final PsiExpression[] expressions = ((PsiExpressionList)pattern).getExpressions();
      final PsiExpression[] childExpressions = ((PsiExpressionList)candidate).getExpressions();
      if (expressions.length > 0 && expressions[expressions.length - 1] instanceof PsiReferenceExpression) {
        final PsiElement resolved = ((PsiReferenceExpression)expressions[expressions.length - 1]).resolve();
        if (resolved instanceof PsiParameter && ((PsiParameter)resolved).getType() instanceof PsiEllipsisType) {
          for(int i = 0; i < expressions.length - 1; i++) {
            final Pair<PsiVariable, PsiType> parameter = expressions[i].getUserData(PARAMETER);
            if (parameter == null) {
              if (!matchPattern(expressions[i], childExpressions[i], candidates, match)) {
                return false;
              }
            } else if (!match.putParameter(parameter, childExpressions[i])) return false;
          }
          final Pair<PsiVariable, PsiType> param = expressions[expressions.length - 1].getUserData(PARAMETER);
          if (param == null) return false;
          for(int i = expressions.length - 1; i < childExpressions.length; i++) {
            if (!match.putParameter(param, childExpressions[i])) return false;
          }
          return true;
        }
      }
    }

    if (pattern instanceof PsiAssignmentExpression) {
      final PsiExpression lExpression = PsiUtil.skipParenthesizedExprDown(((PsiAssignmentExpression)pattern).getLExpression());
      if (lExpression.getType() instanceof PsiPrimitiveType &&
          lExpression instanceof PsiReferenceExpression &&
          ((PsiReferenceExpression)lExpression).resolve() instanceof PsiParameter) {
        return false;
      }
    } else if (pattern instanceof PsiUnaryExpression) {
      if (checkParameterModification(((PsiUnaryExpression)pattern).getOperand(), ((PsiUnaryExpression)pattern).getOperationTokenType(),
                                     ((PsiUnaryExpression)candidate).getOperand())) return false;
    }

    if (pattern instanceof PsiJavaCodeReferenceElement) {
      final PsiElement resolveResult1 = ((PsiJavaCodeReferenceElement)pattern).resolve();
      final PsiElement resolveResult2 = ((PsiJavaCodeReferenceElement)candidate).resolve();
      if (resolveResult1 instanceof PsiClass && resolveResult2 instanceof PsiClass) return true;
      if (isUnder(resolveResult1, myPatternAsList) && isUnder(resolveResult2, candidates)) {
        traverseParameter(resolveResult1, resolveResult2, match);
        return match.putDeclarationCorrespondence(resolveResult1, resolveResult2);
      }
      if (resolveResult1 instanceof PsiVariable && myEffectivelyLocal.contains((PsiVariable)resolveResult1)) {
        return (resolveResult2 instanceof PsiLocalVariable || resolveResult2 instanceof PsiParameter) &&
               match.putDeclarationCorrespondence(resolveResult1, resolveResult2);
      }
      final PsiElement qualifier2 = ((PsiJavaCodeReferenceElement)candidate).getQualifier();
      if (!equivalentResolve(resolveResult1, resolveResult2, qualifier2)) {
        return matchExtractableVariable(pattern, candidate, match);
      }
      PsiElement qualifier1 = ((PsiJavaCodeReferenceElement)pattern).getQualifier();
      if (qualifier1 instanceof PsiReferenceExpression && qualifier2 instanceof PsiReferenceExpression &&
          !match.areCorrespond(((PsiReferenceExpression)qualifier1).resolve(), ((PsiReferenceExpression)qualifier2).resolve())) {
        return false;
      }

      if (qualifier1 == null && qualifier2 == null) {
        final PsiClass patternClass = RefactoringChangeUtil.getThisClass(pattern);
        final PsiClass candidateClass = RefactoringChangeUtil.getThisClass(candidate);
        if (resolveResult1 == resolveResult2 &&
            resolveResult1 instanceof PsiMember) {
          final PsiClass containingClass = ((PsiMember)resolveResult1).getContainingClass();
          if (!InheritanceUtil.isInheritorOrSelf(candidateClass, patternClass, true) &&
              InheritanceUtil.isInheritorOrSelf(candidateClass, containingClass, true) &&
              InheritanceUtil.isInheritorOrSelf(patternClass, containingClass, true)) {
            return false;
          }
        }
      }

    }

    if (pattern instanceof PsiTypeCastExpression) {
      final PsiTypeElement castTypeElement1 = ((PsiTypeCastExpression)pattern).getCastType();
      final PsiTypeElement castTypeElement2 = ((PsiTypeCastExpression)candidate).getCastType();
      if (castTypeElement1 != null && castTypeElement2 != null) {
        final PsiType type1 = TypeConversionUtil.erasure(castTypeElement1.getType());
        final PsiType type2 = TypeConversionUtil.erasure(castTypeElement2.getType());
        if (!type1.equals(type2)) return false;
      }
    } else if (pattern instanceof PsiNewExpression) {
      final PsiType type1 = ((PsiNewExpression)pattern).getType();
      final PsiType type2 = ((PsiNewExpression)candidate).getType();
      if (type1 == null || type2 == null) return false;
      final PsiMethod constructor1 = ((PsiNewExpression)pattern).resolveConstructor();
      final PsiMethod constructor2 = ((PsiNewExpression)candidate).resolveConstructor();
      if (constructor1 != null && constructor2 != null) {
        if (!pattern.getManager().areElementsEquivalent(constructor1, constructor2)) return false;
      }
      else {
        if (!canTypesBeEquivalent(type1, type2)) return false;
      }
    } else if (pattern instanceof PsiClassObjectAccessExpression) {
      final PsiTypeElement operand1 = ((PsiClassObjectAccessExpression)pattern).getOperand();
      final PsiTypeElement operand2 = ((PsiClassObjectAccessExpression)candidate).getOperand();
      return operand1.getType().equals(operand2.getType());
    } else if (pattern instanceof PsiInstanceOfExpression) {
      final PsiTypeElement operand1 = ((PsiInstanceOfExpression)pattern).getCheckType();
      final PsiTypeElement operand2 = ((PsiInstanceOfExpression)candidate).getCheckType();
      if (operand1 == null || operand2 == null) return false;
      if (!operand1.getType().equals(operand2.getType())) return false;
    } else if (pattern instanceof PsiReturnStatement) {
      final PsiReturnStatement patternReturnStatement = (PsiReturnStatement)pattern;
      return matchReturnStatement(patternReturnStatement, candidate, candidates, match);
    } else if (pattern instanceof PsiContinueStatement) {
      match.registerReturnValue(new ContinueReturnValue());
    } else if (pattern instanceof PsiBreakStatement) {
      match.registerReturnValue(new BreakReturnValue());
    }else if (pattern instanceof PsiMethodCallExpression) {
      final PsiMethod patternMethod = ((PsiMethodCallExpression)pattern).resolveMethod();
      final PsiMethod candidateMethod = ((PsiMethodCallExpression)candidate).resolveMethod();
      if (patternMethod != null && candidateMethod != null) {
        if (!MethodSignatureUtil.areSignaturesEqual(patternMethod, candidateMethod)) return false;
      }
    } else if (pattern instanceof PsiReferenceExpression) {
      final PsiReferenceExpression patternRefExpr = (PsiReferenceExpression)pattern;
      final PsiReferenceExpression candidateRefExpr = (PsiReferenceExpression)candidate;
      final PsiExpression patternQualifier = patternRefExpr.getQualifierExpression();
      final PsiExpression candidateQualifier = candidateRefExpr.getQualifierExpression();
      if (patternQualifier == null) {
        PsiClass contextClass = PsiTreeUtil.getContextOfType(pattern, PsiClass.class);
        if (candidateQualifier instanceof PsiReferenceExpression) {
          final PsiElement resolved = ((PsiReferenceExpression)candidateQualifier).resolve();
          if (resolved instanceof PsiClass && contextClass != null && InheritanceUtil.isInheritorOrSelf(contextClass, (PsiClass)resolved, true)) {
            return true;
          }
        }
        return contextClass != null && match.registerInstanceExpression(candidateQualifier, contextClass);
      } else {
        if (candidateQualifier == null) {
          if (patternQualifier instanceof PsiThisExpression) {
            final PsiJavaCodeReferenceElement qualifier = ((PsiThisExpression)patternQualifier).getQualifier();
            if (candidate instanceof PsiReferenceExpression) {
              PsiElement contextClass = qualifier == null ? PsiTreeUtil.getContextOfType(pattern, PsiClass.class) : qualifier.resolve();
              return contextClass instanceof PsiClass && match.registerInstanceExpression(((PsiReferenceExpression)candidate).getQualifierExpression(),
                                                                                          (PsiClass)contextClass);
            }
          } else {
            final PsiType type = patternQualifier.getType();
            PsiClass contextClass = type instanceof PsiClassType ? ((PsiClassType)type).resolve() : null;
            try {
              final Pair<PsiVariable, PsiType> parameter = patternQualifier.getUserData(PARAMETER);

              if (parameter != null) {
                final PsiClass thisClass = RefactoringChangeUtil.getThisClass(parameter.first);

                if (contextClass != null && InheritanceUtil.isInheritorOrSelf(thisClass, contextClass, true)) {
                  contextClass = thisClass;
                }
                final PsiClass thisCandidate = RefactoringChangeUtil.getThisClass(candidate);
                if (thisCandidate != null && InheritanceUtil.isInheritorOrSelf(thisCandidate, contextClass, true)) {
                  contextClass = thisCandidate;
                }
                return contextClass != null && !(contextClass instanceof PsiAnonymousClass) && match.putParameter(parameter, RefactoringChangeUtil
                  .createThisExpression(patternQualifier.getManager(), contextClass));
              } else if (patternQualifier instanceof PsiReferenceExpression) {
                final PsiElement resolved = ((PsiReferenceExpression)patternQualifier).resolve();
                if (resolved instanceof PsiClass) {
                  final PsiClass classContext = PsiTreeUtil.getContextOfType(candidate, PsiClass.class);
                  if (classContext != null && InheritanceUtil.isInheritorOrSelf(classContext, (PsiClass)resolved, true)) {
                    return true;
                  }
                }
              }

              return false;
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        } else {
          if (patternQualifier instanceof PsiThisExpression && candidateQualifier instanceof PsiThisExpression) {
            final PsiJavaCodeReferenceElement thisPatternQualifier = ((PsiThisExpression)patternQualifier).getQualifier();
            final PsiElement patternContextClass = thisPatternQualifier == null ? PsiTreeUtil.getContextOfType(patternQualifier, PsiClass.class) : thisPatternQualifier.resolve();
            final PsiJavaCodeReferenceElement thisCandidateQualifier = ((PsiThisExpression)candidateQualifier).getQualifier();
            final PsiElement candidateContextClass = thisCandidateQualifier == null ? PsiTreeUtil.getContextOfType(candidateQualifier, PsiClass.class) : thisCandidateQualifier.resolve();
            return patternContextClass == candidateContextClass;
          }
        }
      }
    } else if (pattern instanceof PsiThisExpression) {
      final PsiJavaCodeReferenceElement qualifier = ((PsiThisExpression)pattern).getQualifier();
      final PsiElement contextClass = qualifier == null ? PsiTreeUtil.getContextOfType(pattern, PsiClass.class) : qualifier.resolve();
      if (candidate instanceof PsiReferenceExpression) {
        final PsiElement parent = candidate.getParent();
        return parent instanceof PsiReferenceExpression && contextClass instanceof PsiClass && match.registerInstanceExpression(((PsiReferenceExpression)parent).getQualifierExpression(),
                                                                                    (PsiClass)contextClass);
      } else if (candidate instanceof PsiThisExpression) {
        final PsiJavaCodeReferenceElement candidateQualifier = ((PsiThisExpression)candidate).getQualifier();
        final PsiElement candidateContextClass = candidateQualifier == null ? PsiTreeUtil.getContextOfType(candidate, PsiClass.class) : candidateQualifier.resolve();
        return contextClass == candidateContextClass;
      }
    } else if (pattern instanceof PsiSuperExpression) {
      final PsiJavaCodeReferenceElement qualifier = ((PsiSuperExpression)pattern).getQualifier();
      final PsiElement contextClass = qualifier == null ? PsiTreeUtil.getContextOfType(pattern, PsiClass.class) : qualifier.resolve();
      if (candidate instanceof PsiSuperExpression) {
        final PsiJavaCodeReferenceElement candidateQualifier = ((PsiSuperExpression)candidate).getQualifier();
        return contextClass == (candidateQualifier != null ? candidateQualifier.resolve() : PsiTreeUtil.getContextOfType(candidate, PsiClass.class));
      }
    } else if (pattern instanceof PsiModifierList) {
      return candidate instanceof PsiModifierList && matchModifierList((PsiModifierList)pattern, (PsiModifierList)candidate);
    }

    PsiElement[] children1 = getFilteredChildren(pattern);
    PsiElement[] children2 = getFilteredChildren(candidate);
    if (children1.length != children2.length) return false;


    for (int i = 0; i < children1.length; i++) {
      PsiElement child1 = children1[i];
      PsiElement child2 = children2[i];
      if (!matchPattern(child1, child2, candidates, match) &&
          !matchExtractableExpression(child1, child2, candidates, match)) {
        return false;
      }
    }

    if (children1.length == 0) {
      if (pattern.getParent() instanceof PsiVariable && ((PsiVariable)pattern.getParent()).getNameIdentifier() == pattern) {
        return match.putDeclarationCorrespondence(pattern.getParent(), candidate.getParent());
      }
      if (!pattern.textMatches(candidate)) return false;
    }

    return true;
  }

  private boolean matchExtractableExpression(PsiElement pattern, PsiElement candidate,
                                             List<PsiElement> candidates, Match match) {
    if (!myWithExtractedParameters || !(pattern instanceof PsiExpression) || !(candidate instanceof PsiExpression)) {
      return false;
    }

    if (myPatternComplexityHolder == null) {
      myPatternComplexityHolder = new ComplexityHolder(myPatternAsList);
    }
    ExtractableExpressionPart patternPart = ExtractableExpressionPart.match((PsiExpression)pattern, myPatternAsList, myPatternComplexityHolder);
    if (patternPart == null) {
      return false;
    }

    if (myCandidateComplexityHolder == null || myCandidateComplexityHolder.getScope() != candidates) {
      myCandidateComplexityHolder = new ComplexityHolder(candidates);
    }
    ExtractableExpressionPart candidatePart = ExtractableExpressionPart.match((PsiExpression)candidate, candidates, myCandidateComplexityHolder);
    if (candidatePart == null) {
      return false;
    }

    if (patternPart.myValue != null && patternPart.myValue.equals(candidatePart.myValue)) {
      return true;
    }
    if (patternPart.myVariable == null || candidatePart.myVariable == null) {
      return match.putExtractedParameter(patternPart, candidatePart);
    }
    return false;
  }

  private boolean matchExtractableVariable(PsiElement pattern, PsiElement candidate, Match match) {
    if (!myWithExtractedParameters || !(pattern instanceof PsiReferenceExpression) || !(candidate instanceof PsiReferenceExpression)) {
      return false;
    }
    ExtractableExpressionPart part1 = ExtractableExpressionPart.matchVariable((PsiReferenceExpression)pattern, null);
    if (part1 == null || part1.myVariable == null) {
      return false;
    }
    ExtractableExpressionPart part2 = ExtractableExpressionPart.matchVariable((PsiReferenceExpression)candidate, null);
    if (part2 == null || part2.myVariable == null) {
      return false;
    }
    return match.putExtractedParameter(part1, part2);
  }


  private static boolean matchModifierList(PsiModifierList modifierList1, PsiModifierList modifierList2) {
    if (!(modifierList1.getParent() instanceof PsiLocalVariable)) {
      // local variables can only have a final modifier, and are considered equivalent with or without it.
      for (String modifier : PsiModifier.MODIFIERS) {
        if (modifierList1.hasModifierProperty(modifier)) {
          if (!modifierList2.hasModifierProperty(modifier)) {
            return false;
          }
        }
        else if (modifierList2.hasModifierProperty(modifier)) {
          return false;
        }
      }
    }
    return AnnotationUtil.equal(modifierList1.getAnnotations(), modifierList2.getAnnotations());
  }

  private static boolean checkParameterModification(PsiExpression expression,
                                                    final IElementType sign,
                                                    PsiExpression candidate) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    candidate = PsiUtil.skipParenthesizedExprDown(candidate);
    if (expression instanceof PsiReferenceExpression && ((PsiReferenceExpression)expression).resolve() instanceof PsiParameter &&
        (sign.equals(JavaTokenType.MINUSMINUS)|| sign.equals(JavaTokenType.PLUSPLUS))) {
      if (candidate instanceof PsiReferenceExpression && ((PsiReferenceExpression)candidate).resolve() instanceof PsiParameter) {
        return false;
      }
      return true;
    }
    return false;
  }

  private static void traverseParameter(PsiElement pattern, PsiElement candidate, Match match) {
    if (pattern == null || candidate == null) return;
    if (pattern.getUserData(PARAMETER) != null) {
      final Pair<PsiVariable, PsiType> parameter = pattern.getUserData(PARAMETER);
      match.putParameter(parameter, candidate);
      return;
    }

    PsiElement[] children1 = getFilteredChildren(pattern);
    PsiElement[] children2 = getFilteredChildren(candidate);
    if (children1.length != children2.length) return;

    for (int i = 0; i < children1.length; i++) {
      PsiElement child1 = children1[i];
      PsiElement child2 = children2[i];
      traverseParameter(child1, child2, match);
    }
  }

  private boolean matchReturnStatement(final PsiReturnStatement patternReturnStatement,
                                       PsiElement candidate,
                                       List<PsiElement> candidates,
                                       Match match) {
    if (candidate instanceof PsiExpressionStatement) {
      final PsiExpression expression = ((PsiExpressionStatement)candidate).getExpression();
      if (expression instanceof PsiAssignmentExpression) {
        final PsiExpression returnValue = patternReturnStatement.getReturnValue();
        final PsiExpression rExpression = ((PsiAssignmentExpression)expression).getRExpression();
        if (!matchPattern(returnValue, rExpression, candidates, match)) return false;
        final PsiExpression lExpression = ((PsiAssignmentExpression)expression).getLExpression();
        return match.registerReturnValue(new ExpressionReturnValue(lExpression));
      }
      else return false;
    }
    else if (candidate instanceof PsiDeclarationStatement) {
      final PsiElement[] declaredElements = ((PsiDeclarationStatement)candidate).getDeclaredElements();
      if (declaredElements.length != 1) return false;
      if (!(declaredElements[0] instanceof PsiVariable)) return false;
      final PsiVariable variable = (PsiVariable)declaredElements[0];
      if (!matchPattern(patternReturnStatement.getReturnValue(), variable.getInitializer(), candidates, match)) return false;
      return match.registerReturnValue(new VariableReturnValue(variable));
    }
    else if (candidate instanceof PsiReturnStatement) {
      final PsiExpression returnValue = PsiUtil.skipParenthesizedExprDown(((PsiReturnStatement)candidate).getReturnValue());
      if (myMultipleExitPoints) {
        return match.registerReturnValue(new ConditionalReturnStatementValue(returnValue));
      }
      else {
        final PsiElement classOrLambda = PsiTreeUtil.getContextOfType(returnValue, PsiClass.class, PsiLambdaExpression.class);
        final PsiElement commonParent = PsiTreeUtil.findCommonParent(match.getMatchStart(), match.getMatchEnd());
        if (classOrLambda == null || !PsiTreeUtil.isAncestor(commonParent, classOrLambda, false)) {
          if (returnValue != null && !match.registerReturnValue(ReturnStatementReturnValue.INSTANCE)) return false; //do not register return value for return; statement
        }
        return matchPattern(PsiUtil.skipParenthesizedExprDown(patternReturnStatement.getReturnValue()), returnValue, candidates, match);
      }
    }
    else return false;
  }

  private static boolean equivalentResolve(final PsiElement resolveResult1, final PsiElement resolveResult2, PsiElement qualifier2) {
    if (Comparing.equal(resolveResult1, resolveResult2)) return true;
    if (resolveResult1 instanceof PsiMethod && resolveResult2 instanceof PsiMethod) {
      final PsiMethod method1 = (PsiMethod)resolveResult1;
      final PsiMethod method2 = (PsiMethod)resolveResult2;
      if (method1.hasModifierProperty(PsiModifier.STATIC)) return false; // static methods don't inherit
      if (ArrayUtil.find(method1.findSuperMethods(), method2) >= 0) return true;
      if (ArrayUtil.find(method2.findSuperMethods(), method1) >= 0) return true;

      if (method1.getName().equals(method2.getName())) {
        PsiClass class2 = method2.getContainingClass();
        if (qualifier2 instanceof PsiReferenceExpression) {
          final PsiType type = ((PsiReferenceExpression)qualifier2).getType();
          if (type instanceof PsiClassType){
            final PsiClass resolvedClass = PsiUtil.resolveClassInType(type);
            if (!(resolvedClass instanceof PsiTypeParameter)) {
              class2 = resolvedClass;
            }
          }
        }

        if (class2 != null && PsiUtil.isAccessible(method1, class2, null)) {
          final PsiMethod[] methods = class2.getAllMethods();
          if (ArrayUtil.find(methods, method1) != -1) return true;
        }
      }
      return false;
    }
    else {
      return false;
    }
  }

  static boolean isUnder(@Nullable PsiElement element, @NotNull List<PsiElement> parents) {
    if (element == null) return false;
    for (final PsiElement parent : parents) {
      if (PsiTreeUtil.isAncestor(parent, element, false)) return true;
    }
    return false;
  }

  @NotNull
  public static PsiElement[] getFilteredChildren(PsiElement element1) {
    PsiElement[] children1 = element1.getChildren();
    ArrayList<PsiElement> array = new ArrayList<>();
    for (PsiElement child : children1) {
      if (!(child instanceof PsiWhiteSpace) && !(child instanceof PsiComment) && !(child instanceof PsiEmptyStatement)) {
        if (child instanceof PsiBlockStatement) {
          child = ((PsiBlockStatement)child).getCodeBlock();
        }
        if (child instanceof PsiCodeBlock) {
          final PsiStatement[] statements = ((PsiCodeBlock)child).getStatements();
          for (PsiStatement statement : statements) {
            if (statement instanceof PsiBlockStatement) {
              Collections.addAll(array, getFilteredChildren(statement));
            } else if (!(statement instanceof PsiEmptyStatement)) {
              array.add(statement);
            }
          }
          continue;
        } else if (child instanceof PsiParenthesizedExpression) {
          array.add(PsiUtil.skipParenthesizedExprDown((PsiParenthesizedExpression)child));
          continue;
        }
        array.add(child);
      }
    }
    return PsiUtilCore.toPsiElementArray(array);
  }

}
