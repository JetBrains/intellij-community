// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.controlFlow;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.ExpressionUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.Predicates;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.util.FileTypeUtils;
import com.intellij.psi.util.JavaPsiRecordUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import it.unimi.dsi.fastutil.ints.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.IntFunction;
import java.util.function.Predicate;

public final class ControlFlowUtil {
  private static final Logger LOG = Logger.getInstance(ControlFlowUtil.class);

  /**
   * @param variable variable
   * @param context the context that references to the variable
   * @return the scope around context that enforces variable to be effectively final. Currently, it could be
   * an inner class, lambda expression, or {@link PsiSwitchLabelStatementBase} for switch guard. Returns null if there's no such scope, 
   * or the variable declaration is within the same scope, so it should not be effectively final.
   * Note that if null is returned, it doesn't mean that the variable could be modified, as another reference from 
   * another place might exist.
   */
  public static @Nullable PsiElement getScopeEnforcingEffectiveFinality(@NotNull PsiVariable variable, @NotNull PsiElement context) {
    PsiElement[] scope = getVariableScope(variable);
    if (scope.length < 1 || scope[0] == null || scope[0].getContainingFile() != context.getContainingFile()) return null;
    PsiElement parent = context.getParent();
    PsiElement prevParent = context;
    outer:
    while (parent != null) {
      for (PsiElement scopeElement : scope) {
        if (parent.equals(scopeElement)) break outer;
      }
      if (parent instanceof PsiClass && !(prevParent instanceof PsiExpressionList && parent instanceof PsiAnonymousClass)) {
        return parent;
      }
      if (parent instanceof PsiLambdaExpression) {
        return parent;
      }
      if (parent instanceof PsiSwitchLabelStatementBase && ((PsiSwitchLabelStatementBase)parent).getGuardExpression() == prevParent) {
        return parent;
      }
      prevParent = parent;
      parent = parent.getParent();
    }
    return null;
  }

  private static PsiElement @NotNull [] getVariableScope(@NotNull PsiVariable variable) {
    PsiElement[] scope;
    if (variable instanceof PsiResourceVariable) {
      scope = ((PsiResourceVariable)variable).getDeclarationScope();
    }
    else if (variable instanceof PsiLocalVariable) {
      PsiElement parent = variable.getParent();
      scope = new PsiElement[]{parent != null ? parent.getParent() : null}; // code block or for statement
    }
    else if (variable instanceof PsiParameter) {
      scope = new PsiElement[]{((PsiParameter)variable).getDeclarationScope()};
    }
    else {
      scope = new PsiElement[]{variable.getParent()};
    }
    return scope;
  }

  /**
   * @param variable variable to check
   * @param scope variable scope
   * @return true if the variable is effectively final
   */
  public static boolean isEffectivelyFinal(@NotNull PsiVariable variable, @NotNull PsiElement scope) {
    return isEffectivelyFinal(variable, scope, null);
  }

  /**
   * @param variable variable to check
   * @param scope variable scope
   * @param context context element (actual for fields, ignored for local variables and parameters)
   * @return true if the variable is effectively final
   */
  public static boolean isEffectivelyFinal(@NotNull PsiVariable variable, @NotNull PsiElement scope, 
                                           @Nullable PsiJavaCodeReferenceElement context) {
    boolean effectivelyFinal;
    if (variable instanceof PsiParameter) {
      effectivelyFinal = !variableIsAssigned(variable, ((PsiParameter)variable).getDeclarationScope());
    }
    else {
      PsiElement codeBlock = PsiUtil.getVariableCodeBlock(variable, context);
      if (codeBlock == null) return true;
      ControlFlow controlFlow = getControlFlow(codeBlock);
      if (controlFlow == null) return true;

      Collection<VariableInfo> initializedTwice = getInitializedTwice(controlFlow);
      effectivelyFinal = !initializedTwice.contains(new VariableInfo(variable, null));
      if (effectivelyFinal) {
        for (PsiReferenceExpression expression : getReadBeforeWriteLocals(controlFlow)) {
          if (expression.resolve() == variable) {
            return PsiUtil.isAccessedForReading(expression);
          }
        }
        effectivelyFinal = !variableIsAssigned(variable, scope);
        if (effectivelyFinal) {
          // TODO: check; probably this traversal is redundant
          Ref<Boolean> stopped = new Ref<>(false);
          codeBlock.accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
              if (expression.isReferenceTo(variable) &&
                  PsiUtil.isAccessedForWriting(expression) &&
                  isVariableAssignedInLoop(expression, variable)) {
                stopWalking();
                stopped.set(true);
              }
            }
          });
          return !stopped.get();
        }
      }
    }
    return effectivelyFinal;
  }

  private static @Nullable ControlFlow getControlFlow(PsiElement codeBlock) {
    try {
      LocalsOrMyInstanceFieldsControlFlowPolicy policy = LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance();
      return ControlFlowFactory.getControlFlow(codeBlock, policy, ControlFlowOptions.create(true, true, true));
    }
    catch (AnalysisCanceledException e) {
      return null;
    }
  }

  private static boolean variableIsAssigned(@NotNull PsiVariable variable, @NotNull PsiElement scope) {
    return !PsiTreeUtil.processElements(scope, PsiReferenceExpression.class, 
                                        e -> !(PsiUtil.isAccessedForWriting(e) && e.isReferenceTo(variable)));
  }

  /**
   * @param field field to check
   * @return true if the field is initialized (in class initializer, own initializer, another field initializer, or constructor)
   */
  public static boolean isFieldInitializedAfterObjectConstruction(@NotNull PsiField field) {
    if (field.hasInitializer()) return true;
    boolean isFieldStatic = field.hasModifierProperty(PsiModifier.STATIC);
    PsiClass aClass = field.getContainingClass();
    if (aClass == null) return false;
    // field might be assigned in the other field initializers
    if (isFieldInitializedInOtherFieldInitializer(aClass, field, Predicates.alwaysTrue())) return true;
    if (isFieldInitializedInClassInitializer(field)) return true;
    if (isFieldStatic) return false;
    // instance field should be initialized at the end of each constructor
    PsiMethod[] constructors = aClass.getConstructors();

    if (constructors.length == 0) return false;
    nextConstructor:
    for (PsiMethod constructor : constructors) {
      PsiCodeBlock ctrBody = constructor.getBody();
      if (ctrBody == null) return false;
      for (PsiMethod redirectedConstructor : JavaPsiConstructorUtil.getChainedConstructors(constructor)) {
        PsiCodeBlock body = redirectedConstructor.getBody();
        if (body != null && variableDefinitelyAssignedIn(field, body, true)) continue nextConstructor;
      }
      if (!ctrBody.isValid() || variableDefinitelyAssignedIn(field, ctrBody, true)) {
        continue;
      }
      return false;
    }
    return true;
  }
  
  /**
   * @param field field to check
   * @return true if the field is initialized in its class initializers
   */
  private static boolean isFieldInitializedInClassInitializer(@NotNull PsiField field) {
    PsiClass aClass = field.getContainingClass();
    if (aClass == null) return false;
    PsiClassInitializer[] initializers = aClass.getInitializers();
    boolean isFieldStatic = field.hasModifierProperty(PsiModifier.STATIC);
    return ContainerUtil.find(initializers, initializer -> initializer.hasModifierProperty(PsiModifier.STATIC) == isFieldStatic
                                                           && variableDefinitelyAssignedIn(field, initializer.getBody(), true)) != null;
  }

  private static boolean isFieldInitializedInOtherFieldInitializer(@NotNull PsiClass aClass,
                                                                   @NotNull PsiField field,
                                                                   @NotNull Predicate<? super PsiField> condition) {
    boolean fieldStatic = field.hasModifierProperty(PsiModifier.STATIC);
    for (PsiField psiField : aClass.getFields()) {
      if (psiField != field
          && psiField.hasModifierProperty(PsiModifier.STATIC) == fieldStatic
          && variableDefinitelyAssignedIn(field, psiField, true)
          && condition.test(psiField)) {
        return true;
      }
    }
    return false;
  }

  /**
   * @return field that has initializer with this element as subexpression or null if not found
   */
  private static PsiField findEnclosingFieldInitializer(@NotNull PsiElement entry) {
    PsiElement element = entry;
    while (element != null) {
      PsiElement parent = element.getParent();
      if (parent instanceof PsiField) {
        PsiField field = (PsiField)parent;
        if (element == field.getInitializer()) return field;
        if (field instanceof PsiEnumConstant && element == ((PsiEnumConstant)field).getArgumentList()) return field;
      }
      if (element instanceof PsiClass || element instanceof PsiMethod) return null;
      element = parent;
    }
    return null;
  }

  /**
   * see JLS chapter 16
   * @param variable variable to check
   * @param scope variable scope (code block, field initializer, etc.)
   * @return true if variable assigned (maybe more than once)
   */
  public static boolean variableDefinitelyAssignedIn(@NotNull PsiVariable variable, @NotNull PsiElement scope) {
    return variableDefinitelyAssignedIn(variable, scope, false);
  }

  private static boolean variableDefinitelyAssignedIn(@NotNull PsiVariable variable,
                                                      @NotNull PsiElement scope,
                                                      boolean resultOnIncompleteCode) {
    ControlFlow flow = getControlFlow(scope);
    return flow == null ? resultOnIncompleteCode : isVariableDefinitelyAssigned(variable, flow);
  }

  /**
   * @param expression variable reference (usage)
   * @param variable variable
   * @param uninitializedVarProblems map to cache results from the same code block
   * @param treatNonFinalFieldsAsNonInitialized if true, the non-final field will not be considered as initialized with the default value
   * @return true if the variable is initialized before usage
   */
  public static boolean isInitializedBeforeUsage(@NotNull PsiReferenceExpression expression,
                                                 @NotNull PsiVariable variable,
                                                 @NotNull Map<? super PsiElement, Collection<PsiReferenceExpression>> uninitializedVarProblems,
                                                 boolean treatNonFinalFieldsAsNonInitialized) {
    if (variable instanceof ImplicitVariable) return true;
    if (!PsiUtil.isAccessedForReading(expression)) return true;
    int startOffset = expression.getTextRange().getStartOffset();
    PsiElement topBlock = getTopBlock(expression, variable);
    if (topBlock == null) return true;
    if (!variable.hasInitializer()) {
      if (variable instanceof PsiField) {
        PsiField field = (PsiField)variable;
        // non-final field already initialized with default value
        if (!treatNonFinalFieldsAsNonInitialized && !variable.hasModifierProperty(PsiModifier.FINAL)) return true;
        // a final field may be initialized in ctor or class initializer only
        // if we're inside a non-constructor method, skip it
        if (PsiUtil.findEnclosingConstructorOrInitializer(expression) == null
            && findEnclosingFieldInitializer(expression) == null) {
          return true;
        }
        PsiElement parent = topBlock.getParent();
        // access to final fields from inner classes always allowed
        if (inInnerClass(expression, field.getContainingClass())) return true;
        PsiCodeBlock block;
        PsiClass aClass;
        if (parent instanceof PsiMethod) {
          PsiMethod constructor = (PsiMethod)parent;
          if (!constructor.getManager().areElementsEquivalent(constructor.getContainingClass(), field.getContainingClass())) return true;
          // static variables already initialized in class initializers
          if (variable.hasModifierProperty(PsiModifier.STATIC)) return true;
          // as a last chance, the field may be initialized in this() call
          for (PsiMethod redirectedConstructor : JavaPsiConstructorUtil.getChainedConstructors(constructor)) {
            // variable must be initialized before its usage
            //???
            //if (startOffset < redirectedConstructor.getTextRange().getStartOffset()) continue;
            if (JavaPsiRecordUtil.isCompactConstructor(redirectedConstructor)) return true;
            PsiCodeBlock body = redirectedConstructor.getBody();
            if (body != null && variableDefinitelyAssignedIn(variable, body, true)) {
              return true;
            }
          }
          block = constructor.getBody();
          aClass = constructor.getContainingClass();
        }
        else if (parent instanceof PsiClassInitializer) {
          PsiClassInitializer classInitializer = (PsiClassInitializer)parent;
          if (!classInitializer.getManager().areElementsEquivalent(classInitializer.getContainingClass(), field.getContainingClass())) {
            return true;
          }
          block = classInitializer.getBody();
          aClass = classInitializer.getContainingClass();

          if (aClass == null || isFieldInitializedInOtherFieldInitializer(aClass, field, f -> startOffset > f.getTextOffset())) {
            return true;
          }
        }
        else {
          // field reference outside code block
          // check variable initialized before its usage
          aClass = field.getContainingClass();
          PsiField anotherField = PsiTreeUtil.getTopmostParentOfType(expression, PsiField.class);
          if (aClass == null ||
              isFieldInitializedInOtherFieldInitializer(aClass, field, f -> f != anotherField && startOffset > f.getTextOffset())) {
            return true;
          }
          if (anotherField != null
              && !anotherField.hasModifierProperty(PsiModifier.STATIC)
              && field.hasModifierProperty(PsiModifier.STATIC)
              && isFieldInitializedInClassInitializer(field)) {
            return true;
          }
          if (anotherField != null && anotherField.hasInitializer() && !PsiAugmentProvider.canTrustFieldInitializer(anotherField)) {
            return true;
          }

          int offset = startOffset;
          if (anotherField != null && anotherField.getContainingClass() == aClass && !field.hasModifierProperty(PsiModifier.STATIC)) {
            offset = 0;
          }
          block = null;
          // initializers will be checked later
          for (PsiMethod constructor : aClass.getConstructors()) {
            // the variable must be initialized before its usage
            if (offset < constructor.getTextRange().getStartOffset()) continue;
            PsiCodeBlock body = constructor.getBody();
            if (body != null && variableDefinitelyAssignedIn(variable, body)) {
              return true;
            }
            // as a last chance, the field may be initialized in this() call
            for (PsiMethod redirectedConstructor : JavaPsiConstructorUtil.getChainedConstructors(constructor)) {
              // the variable must be initialized before its usage
              if (offset < redirectedConstructor.getTextRange().getStartOffset()) continue;
              PsiCodeBlock redirectedBody = redirectedConstructor.getBody();
              if (redirectedBody != null && variableDefinitelyAssignedIn(variable, redirectedBody)) {
                return true;
              }
            }
          }
        }

        if (aClass != null) {
          // field may be initialized in class initializer
          for (PsiClassInitializer initializer : aClass.getInitializers()) {
            PsiCodeBlock body = initializer.getBody();
            if (body == block) break;
            // variable referenced in initializer must be initialized in initializer preceding assignment
            // variable referenced in field initializer or in class initializer
            boolean shouldCheckInitializerOrder = block == null || block.getParent() instanceof PsiClassInitializer;
            if (shouldCheckInitializerOrder && startOffset < initializer.getTextRange().getStartOffset()) continue;
            if (initializer.hasModifierProperty(PsiModifier.STATIC) == variable.hasModifierProperty(PsiModifier.STATIC)) {
              if (variableDefinitelyAssignedIn(variable, body)) return true;
            }
          }
        }
      }
    }
    Collection<PsiReferenceExpression> codeBlockProblems = uninitializedVarProblems.get(topBlock);
    if (codeBlockProblems == null) {
      try {
        ControlFlow controlFlow = getControlFlow(topBlock);
        codeBlockProblems = controlFlow == null ? Collections.emptyList() : getReadBeforeWriteLocals(controlFlow);
      }
      catch (IndexNotReadyException e) {
        codeBlockProblems = Collections.emptyList();
      }
      uninitializedVarProblems.put(topBlock, codeBlockProblems);
    }
    return !codeBlockProblems.contains(expression);
  }

  private static @Nullable PsiElement getTopBlock(@NotNull PsiReferenceExpression expression, @NotNull PsiVariable variable) {
    if (variable.hasInitializer()) {
      return PsiUtil.getVariableCodeBlock(variable, variable);
    }
    PsiElement scope = variable instanceof PsiField
                       ? ((PsiField)variable).getContainingClass()
                       : variable.getParent() != null ? variable.getParent().getParent() : null;
    while (scope instanceof PsiCodeBlock && scope.getParent() instanceof PsiSwitchBlock) {
      scope = PsiTreeUtil.getParentOfType(scope, PsiCodeBlock.class);
    }

    return FileTypeUtils.isInServerPageFile(scope) && scope instanceof PsiFile
                 ? scope
                 : PsiUtil.getTopLevelEnclosingCodeBlock(expression, scope);
  }

  private static boolean inInnerClass(@NotNull PsiElement psiElement, @Nullable PsiClass containingClass) {
    for (PsiElement element = psiElement; element != null; element = element.getParent()) {
      if (element instanceof PsiClass) {
        PsiClass aClass = (PsiClass)element;
        boolean innerClass = !psiElement.getManager().areElementsEquivalent(element, containingClass);
        if (innerClass) {
          if (element instanceof PsiAnonymousClass) {
            if (PsiTreeUtil.isAncestor(((PsiAnonymousClass)element).getArgumentList(), psiElement, false)) {
              continue;
            }
            return !insideClassInitialization(containingClass, aClass);
          }
          PsiLambdaExpression lambdaExpression = PsiTreeUtil.getParentOfType(psiElement, PsiLambdaExpression.class);
          return lambdaExpression == null || !insideClassInitialization(containingClass, aClass);
        }
        return false;
      }
    }
    return false;
  }

  private static boolean insideClassInitialization(@Nullable PsiClass containingClass, PsiClass aClass) {
    PsiMember member = aClass;
    while (member != null) {
      if (member.getContainingClass() == containingClass) {
        return member instanceof PsiField ||
               member instanceof PsiMethod && ((PsiMethod)member).isConstructor() ||
               member instanceof PsiClassInitializer;
      }
      member = PsiTreeUtil.getParentOfType(member, PsiMember.class, true);
    }
    return false;
  }

  private static boolean variableDefinitelyNotAssignedIn(@NotNull PsiVariable variable, @NotNull PsiElement context) {
    ControlFlow flow = getControlFlow(context);
    return flow == null || isVariableDefinitelyNotAssigned(variable, flow);
  }

  /**
   * Kind of double initialization problem
   *
   * @see #findFinalVariableAlreadyInitializedProblem(PsiVariable, PsiReferenceExpression, Map)
   */
  public enum DoubleInitializationProblem {
    NO_PROBLEM,
    /**
     * Final variable is reassigned normally
     */
    NORMAL,
    /**
     * Final variable is reassigned in loop
     */
    IN_LOOP,
    /**
     * Double initialization of a final field due to chained constructor call
     */
    IN_CONSTRUCTOR,
    /**
     * Double initialization of a final field in other initializer
     */
    IN_INITIALIZER,
    /**
     * Double initialization of a final field in other field initializer
     */
    IN_FIELD_INITIALIZER
  }

  /**
   * @param variable         final variable to check
   * @param expression       variable reference (write location)
   * @param finalVarProblems a map to cache the results
   * @return DoubleInitializationProblem object that depicts the problem kind
   */
  public static @NotNull DoubleInitializationProblem findFinalVariableAlreadyInitializedProblem(
    @NotNull PsiVariable variable,
    @NotNull PsiReferenceExpression expression,
    @NotNull Map<PsiElement, Collection<VariableInfo>> finalVarProblems) {
    if (!PsiUtil.isAccessedForWriting(expression)) return DoubleInitializationProblem.NO_PROBLEM;

    PsiElement scope = variable instanceof PsiField
                       ? variable.getParent()
                       : variable.getParent() == null ? null : variable.getParent().getParent();
    PsiElement codeBlock = PsiUtil.getTopLevelEnclosingCodeBlock(expression, scope);
    if (codeBlock == null) return DoubleInitializationProblem.NO_PROBLEM;
    Collection<VariableInfo> codeBlockProblems = getFinalVariableProblemsInBlock(finalVarProblems, codeBlock);

    VariableInfo variableInfo = ContainerUtil.find(codeBlockProblems, vi -> vi.expression == expression);
    if (variableInfo == null) {
      if (variable instanceof PsiField) {
        DoubleInitializationProblem problem = isFieldInitializedInAnotherMember((PsiField)variable, expression, codeBlock);
        if (problem != null) {
          return problem;
        }
      }
      return DoubleInitializationProblem.NO_PROBLEM;
    }
    return variableInfo instanceof InitializedInLoopProblemInfo ? DoubleInitializationProblem.IN_LOOP : DoubleInitializationProblem.NORMAL;
  }

  private static DoubleInitializationProblem isFieldInitializedInAnotherMember(@NotNull PsiField field,
                                                                               @NotNull PsiReferenceExpression expression,
                                                                               @NotNull PsiElement codeBlock) {
    PsiClass aClass = field.getContainingClass();
    if (aClass == null) return null;
    boolean isFieldStatic = field.hasModifierProperty(PsiModifier.STATIC);
    PsiMember enclosingConstructorOrInitializer = PsiUtil.findEnclosingConstructorOrInitializer(expression);

    if (!isFieldStatic) {
      // constructor that delegates to another constructor cannot assign final fields
      if (enclosingConstructorOrInitializer instanceof PsiMethod) {
        PsiMethodCallExpression chainedCall = JavaPsiConstructorUtil.findThisOrSuperCallInConstructor(
          (PsiMethod)enclosingConstructorOrInitializer);
        if (JavaPsiConstructorUtil.isChainedConstructorCall(chainedCall)) {
          return DoubleInitializationProblem.IN_CONSTRUCTOR;
        }
      }
    }

    // field can get assigned in other field initializers or in class initializers
    List<PsiMember> members = new ArrayList<>(Arrays.asList(aClass.getFields()));
    if (enclosingConstructorOrInitializer != null
        && aClass.getManager().areElementsEquivalent(enclosingConstructorOrInitializer.getContainingClass(), aClass)) {
      members.addAll(Arrays.asList(aClass.getInitializers()));
      members.sort(PsiUtil.BY_POSITION);
    }

    for (PsiMember member : members) {
      if (member == field) continue;
      PsiElement context = member instanceof PsiField ? ((PsiField)member).getInitializer() : ((PsiClassInitializer)member).getBody();

      if (context != null
          && member.hasModifierProperty(PsiModifier.STATIC) == isFieldStatic
          && !variableDefinitelyNotAssignedIn(field, context)) {
        return context == codeBlock ? null :
               member instanceof PsiField ? DoubleInitializationProblem.IN_FIELD_INITIALIZER :
               DoubleInitializationProblem.IN_INITIALIZER;
      }
    }
    return null;
  }

  private static @NotNull Collection<VariableInfo> getFinalVariableProblemsInBlock(
    @NotNull Map<PsiElement, Collection<VariableInfo>> finalVarProblems, @NotNull PsiElement codeBlock) {
    Collection<VariableInfo> codeBlockProblems =
      finalVarProblems.computeIfAbsent(codeBlock, cb -> {
        ControlFlow controlFlow = getControlFlow(codeBlock);
        return controlFlow == null ? Collections.emptyList() : addReassignedInLoopProblems(getInitializedTwice(controlFlow), controlFlow);
      });
    return codeBlockProblems;
  }

  private static Collection<VariableInfo> addReassignedInLoopProblems(
    @NotNull Collection<VariableInfo> codeBlockProblems,
    @NotNull ControlFlow controlFlow) {
    List<Instruction> instructions = controlFlow.getInstructions();
    for (int index = 0; index < instructions.size(); index++) {
      Instruction instruction = instructions.get(index);
      if (instruction instanceof WriteVariableInstruction) {
        PsiVariable variable = ((WriteVariableInstruction)instruction).variable;
        if (variable instanceof PsiLocalVariable || variable instanceof PsiField) {
          PsiElement anchor = controlFlow.getElement(index);
          if (anchor instanceof PsiAssignmentExpression) {
            PsiExpression ref = PsiUtil.skipParenthesizedExprDown(((PsiAssignmentExpression)anchor).getLExpression());
            if (ref instanceof PsiReferenceExpression) {
              VariableInfo varInfo = new InitializedInLoopProblemInfo(variable, ref);
              if (!codeBlockProblems.contains(varInfo) && isInstructionReachable(controlFlow, index, index)) {
                if (!(codeBlockProblems instanceof HashSet)) {
                  codeBlockProblems = new HashSet<>(codeBlockProblems);
                }
                codeBlockProblems.add(varInfo);
              }
            }
          }
        }
      }
    }
    return codeBlockProblems;
  }

  /**
   * @param variable         variable to check (local variable or parameter)
   * @param finalVarProblems cache map to reuse information
   * @return true if the variable is reassigned
   */
  public static boolean isReassigned(@NotNull PsiVariable variable, @NotNull Map<PsiElement, Collection<VariableInfo>> finalVarProblems) {
    if (variable instanceof PsiLocalVariable) {
      PsiElement parent = variable.getParent();
      if (parent == null) return false;
      PsiElement declarationScope = parent.getParent();
      if (declarationScope == null) return false;
      Collection<VariableInfo> codeBlockProblems = getFinalVariableProblemsInBlock(finalVarProblems, declarationScope);
      return codeBlockProblems.contains(new VariableInfo(variable, null));
    }
    if (variable instanceof PsiParameter) {
      PsiParameter parameter = (PsiParameter)variable;
      return variableIsAssigned(parameter, parameter.getDeclarationScope());
    }
    return false;
  }

  private static class SSAInstructionState {
    private final int myWriteCount;
    private final int myInstructionIdx;

    SSAInstructionState(int writeCount, int instructionIdx) {
      myWriteCount = writeCount;
      myInstructionIdx = instructionIdx;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof SSAInstructionState)) return false;

      final SSAInstructionState ssaInstructionState = (SSAInstructionState)o;

      if (myInstructionIdx != ssaInstructionState.myInstructionIdx) return false;
      return Math.min(2, myWriteCount) == Math.min(2, ssaInstructionState.myWriteCount);
    }

    @Override
    public int hashCode() {
      return 29 * Math.min(2, myWriteCount) + myInstructionIdx;
    }

    int getWriteCount() {
      return myWriteCount;
    }

    int getInstructionIdx() {
      return myInstructionIdx;
    }
  }

  public static @NotNull List<PsiVariable> getSSAVariables(@NotNull ControlFlow flow) {
    return getSSAVariables(flow, 0, flow.getSize(), false);
  }

  public static @NotNull List<PsiVariable> getSSAVariables(@NotNull ControlFlow flow, int from, int to,
                                                           boolean reportVarsIfNonInitializingPathExists) {
    List<Instruction> instructions = flow.getInstructions();
    Collection<PsiVariable> writtenVariables = getWrittenVariables(flow, from, to, false);
    List<PsiVariable> result = new ArrayList<>(1);

    variables:
    for (PsiVariable psiVariable : writtenVariables) {
      PsiManager psiManager = psiVariable.getManager();
      final List<SSAInstructionState> queue = new ArrayList<>();
      queue.add(new SSAInstructionState(0, from));
      Set<SSAInstructionState> processedStates = new HashSet<>();

      while (!queue.isEmpty()) {
        final SSAInstructionState state = queue.remove(0);
        if (state.getWriteCount() > 1) continue variables;
        if (!processedStates.contains(state)) {
          processedStates.add(state);
          int i = state.getInstructionIdx();
          if (i < to) {
            Instruction instruction = instructions.get(i);

            if (instruction instanceof ReturnInstruction) {
              int[] offsets = ((ReturnInstruction)instruction).getPossibleReturnOffsets();
              for (int offset : offsets) {
                queue.add(new SSAInstructionState(state.getWriteCount(), Math.min(offset, to)));
              }
            }
            else if (instruction instanceof GoToInstruction) {
              int nextOffset = ((GoToInstruction)instruction).offset;
              nextOffset = Math.min(nextOffset, to);
              queue.add(new SSAInstructionState(state.getWriteCount(), nextOffset));
            }
            else if (instruction instanceof ThrowToInstruction) {
              int nextOffset = ((ThrowToInstruction)instruction).offset;
              nextOffset = Math.min(nextOffset, to);
              queue.add(new SSAInstructionState(state.getWriteCount(), nextOffset));
            }
            else if (instruction instanceof ConditionalGoToInstruction) {
              int nextOffset = ((ConditionalGoToInstruction)instruction).offset;
              nextOffset = Math.min(nextOffset, to);
              queue.add(new SSAInstructionState(state.getWriteCount(), nextOffset));
              queue.add(new SSAInstructionState(state.getWriteCount(), i + 1));
            }
            else if (instruction instanceof ConditionalThrowToInstruction) {
              int nextOffset = ((ConditionalThrowToInstruction)instruction).offset;
              nextOffset = Math.min(nextOffset, to);
              queue.add(new SSAInstructionState(state.getWriteCount(), nextOffset));
              queue.add(new SSAInstructionState(state.getWriteCount(), i + 1));
            }
            else if (instruction instanceof WriteVariableInstruction) {
              WriteVariableInstruction write = (WriteVariableInstruction)instruction;
              queue.add(new SSAInstructionState(state.getWriteCount() + (psiManager.areElementsEquivalent(write.variable, psiVariable) ? 1 : 0), i + 1));
            }
            else if (instruction instanceof ReadVariableInstruction) {
              ReadVariableInstruction read = (ReadVariableInstruction)instruction;
              if (psiManager.areElementsEquivalent(read.variable, psiVariable) && state.getWriteCount() == 0) continue variables;
              queue.add(new SSAInstructionState(state.getWriteCount(), i + 1));
            }
            else {
              queue.add(new SSAInstructionState(state.getWriteCount(), i + 1));
            }
          }
          else if (!reportVarsIfNonInitializingPathExists && state.getWriteCount() == 0) continue variables;
        }
      }

      result.add(psiVariable);
    }

    return result;
  }

  public static boolean needVariableValueAt(@NotNull PsiVariable variable, @NotNull ControlFlow flow, int offset) {
    InstructionClientVisitor<Boolean> visitor = new InstructionClientVisitor<Boolean>() {
      final boolean[] neededBelow = new boolean[flow.getSize() + 1];

      @Override
      public void procedureEntered(int startOffset, int endOffset) {
        for (int i = startOffset; i < endOffset; i++) neededBelow[i] = false;
      }

      @Override
      public void visitReadVariableInstruction(ReadVariableInstruction instruction, int offset, int nextOffset) {
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();
        boolean needed = neededBelow[nextOffset];
        if (instruction.variable.equals(variable)) {
          needed = true;
        }
        neededBelow[offset] |= needed;
      }

      @Override
      public void visitWriteVariableInstruction(WriteVariableInstruction instruction, int offset, int nextOffset) {
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();
        boolean needed = neededBelow[nextOffset];
        if (instruction.variable.equals(variable)) {
          needed = false;
        }
        neededBelow[offset] = needed;
      }

      @Override
      public void visitInstruction(Instruction instruction, int offset, int nextOffset) {
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();
        boolean needed = neededBelow[nextOffset];
        neededBelow[offset] |= needed;
      }

      @Override
      public Boolean getResult() {
        return neededBelow[offset];
      }
    };
    depthFirstSearch(flow, visitor, offset, flow.getSize());
    return visitor.getResult();
  }

  public static @NotNull Collection<PsiVariable> getWrittenVariables(@NotNull ControlFlow flow, int start, int end, 
                                                                     boolean ignoreNotReachingWrites) {
    Set<PsiVariable> set = new HashSet<>();
    getWrittenVariables(flow, start, end, ignoreNotReachingWrites, set);
    return set;
  }

  public static void getWrittenVariables(@NotNull ControlFlow flow,
                                         int start,
                                         int end,
                                         boolean ignoreNotReachingWrites,
                                         @NotNull Collection<? super PsiVariable> set) {
    List<Instruction> instructions = flow.getInstructions();
    for (int i = start; i < end; i++) {
      Instruction instruction = instructions.get(i);
      if (instruction instanceof WriteVariableInstruction && (!ignoreNotReachingWrites || isInstructionReachable(flow, end, i))) {
        set.add(((WriteVariableInstruction)instruction).variable);
      }
    }
  }

  public static @NotNull List<PsiVariable> getUsedVariables(@NotNull ControlFlow flow, int start, int end) {
    List<PsiVariable> array = new ArrayList<>();
    if (start < 0) return array;
    List<Instruction> instructions = flow.getInstructions();
    for (int i = start; i < end; i++) {
      Instruction instruction = instructions.get(i);
      if (instruction instanceof ReadVariableInstruction) {
        PsiVariable variable = ((ReadVariableInstruction)instruction).variable;
        if (!array.contains(variable)) {
          array.add(variable);
        }
      }
      else if (instruction instanceof WriteVariableInstruction) {
        PsiVariable variable = ((WriteVariableInstruction)instruction).variable;
        if (!array.contains(variable)) {
          array.add(variable);
        }
      }
    }
    return array;
  }

  public static boolean isVariableUsed(@NotNull ControlFlow flow, int start, int end, @NotNull PsiVariable variable) {
    List<Instruction> instructions = flow.getInstructions();
    LOG.assertTrue(start >= 0, "flow start");
    LOG.assertTrue(end <= instructions.size(), "flow end");

    PsiManager psiManager = variable.getManager();
    for (int i = start; i < end; i++) {
      Instruction instruction = instructions.get(i);
      if (instruction instanceof ReadVariableInstruction) {
        if (psiManager.areElementsEquivalent(((ReadVariableInstruction)instruction).variable, variable)) {
          return true;
        }
      }
      else if (instruction instanceof WriteVariableInstruction) {
        if (psiManager.areElementsEquivalent(((WriteVariableInstruction)instruction).variable, variable)) {
          return true;
        }
      }
    }
    return false;
  }

  private static int findSingleReadOffset(@NotNull ControlFlow flow, int startOffset, int endOffset, @NotNull PsiVariable variable) {
    List<Instruction> instructions = flow.getInstructions();
    if (startOffset < 0 || endOffset < 0 || endOffset > instructions.size()) return -1;

    PsiManager psiManager = variable.getManager();
    int readOffset = -1;
    for (int i = startOffset; i < endOffset; i++) {
      Instruction instruction = instructions.get(i);
      if (instruction instanceof ReadVariableInstruction) {
        if (psiManager.areElementsEquivalent(((ReadVariableInstruction)instruction).variable, variable)) {
          if (readOffset < 0) {
            readOffset = i;
          }
          else {
            return -1;
          }
        }
      }
      else if (instruction instanceof WriteVariableInstruction) {
        if (psiManager.areElementsEquivalent(((WriteVariableInstruction)instruction).variable, variable)) {
          return -1;
        }
      }
    }
    return readOffset;
  }

  /**
   * If the variable occurs only once in the element, and it's read access return that occurrence
   */
  public static PsiReferenceExpression findSingleReadOccurrence(@NotNull ControlFlow flow,
                                                                @NotNull PsiElement element,
                                                                @NotNull PsiVariable variable) {
    int readOffset = findSingleReadOffset(flow, flow.getStartOffset(element), flow.getEndOffset(element), variable);
    if (readOffset >= 0) {
      PsiElement readElement = flow.getElement(readOffset);
      readElement = PsiTreeUtil.findFirstParent(readElement, false, e -> e == element || e instanceof PsiReferenceExpression);
      if (readElement instanceof PsiReferenceExpression) {
        return (PsiReferenceExpression)readElement;
      }
    }
    return null;
  }

  public static boolean isVariableReadInFinally(@NotNull ControlFlow flow,
                                                @Nullable PsiElement startElement,
                                                @NotNull PsiElement enclosingCodeFragment,
                                                @NotNull PsiVariable variable) {
    PsiManager psiManager = variable.getManager();
    for (PsiElement element = startElement; element != null && element != enclosingCodeFragment; element = element.getParent()) {
      if (element instanceof PsiCodeBlock) {
        final PsiElement parent = element.getParent();
        if (parent instanceof PsiTryStatement) {
          final PsiTryStatement tryStatement = (PsiTryStatement)parent;
          if (tryStatement.getTryBlock() == element) {
            final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
            if (finallyBlock != null) {
              final List<Instruction> instructions = flow.getInstructions();
              final int startOffset = flow.getStartOffset(finallyBlock);
              final int endOffset = flow.getEndOffset(finallyBlock);
              LOG.assertTrue(startOffset >= 0, "flow start");
              LOG.assertTrue(endOffset <= instructions.size(), "flow end");
              for (int i = startOffset; i < endOffset; i++) {
                final Instruction instruction = instructions.get(i);
                if (instruction instanceof ReadVariableInstruction && psiManager.areElementsEquivalent(((ReadVariableInstruction)instruction).variable, variable)) {
                  return true;
                }
              }
            }
          }
        }
      }
    }
    return false;
  }

  public static @NotNull List<PsiVariable> getInputVariables(@NotNull ControlFlow flow, int start, int end) {
    List<PsiVariable> usedVariables = getUsedVariables(flow, start, end);
    List<PsiVariable> array = new ArrayList<>(usedVariables.size());
    for (PsiVariable variable : usedVariables) {
      if (needVariableValueAt(variable, flow, start)) {
        array.add(variable);
      }
    }
    return array;
  }

  public static PsiVariable @NotNull [] getOutputVariables(@NotNull ControlFlow flow, int start, int end, int @NotNull [] exitPoints) {
    return getOutputVariables(flow, start, end, exitPoints, Integer.MAX_VALUE);
  }

  /**
   * No more than the specified limit output variables.
   */
  public static PsiVariable @NotNull [] getOutputVariables(@NotNull ControlFlow flow, int start, int end, int @NotNull [] exitPoints,
                                                           int limit) {
    Collection<PsiVariable> writtenVariables = getWrittenVariables(flow, start, end, false);
    List<PsiVariable> array = new ArrayList<>();
    outer: for (PsiVariable variable : writtenVariables) {
      for (int exitPoint : exitPoints) {
        if (needVariableValueAt(variable, flow, exitPoint)) {
          array.add(variable);
          if (array.size() >= limit) break outer;
        }
      }
    }
    PsiVariable[] outputVariables = array.toArray(new PsiVariable[0]);
    if (LOG.isDebugEnabled()) {
      LOG.debug("output variables:");
      for (PsiVariable variable : outputVariables) {
        LOG.debug("  " + variable);
      }
    }
    return outputVariables;
  }

  @SafeVarargs
  public static @NotNull Collection<PsiStatement> findExitPointsAndStatements(@NotNull ControlFlow flow, int start, int end,
                                                                              @NotNull IntList exitPoints,
                                                                              Class<? extends PsiStatement> @NotNull ... classesFilter) {
    if (end == start) {
      exitPoints.add(end);
      return Collections.emptyList();
    }
    final Collection<PsiStatement> exitStatements = new HashSet<>();
    InstructionClientVisitor<Void> visitor = new InstructionClientVisitor<Void>() {
      @Override
      public void visitThrowToInstruction(ThrowToInstruction instruction, int offset, int nextOffset) {
        //[ven]This is a hack since Extract Method doesn't want to see throw's exit points
        processGotoStatement(exitStatements, findStatement(flow, offset), classesFilter);
      }

      @Override
      public void visitBranchingInstruction(BranchingInstruction instruction, int offset, int nextOffset) {
        processGoto(flow, start, end, exitPoints, exitStatements, instruction, findStatement(flow, offset), classesFilter);
      }

      // call/return do not incur exit points
      @Override
      public void visitReturnInstruction(ReturnInstruction instruction, int offset, int nextOffset) {
      }

      @Override
      public void visitCallInstruction(CallInstruction instruction, int offset, int nextOffset) {
      }

      @Override
      public void visitConditionalThrowToInstruction(ConditionalThrowToInstruction instruction, int offset, int nextOffset) {
        visitInstruction(instruction, offset, nextOffset);
      }

      @Override
      public void visitInstruction(Instruction instruction, int offset, int nextOffset) {
        if (offset >= end - 1) {
          int exitOffset = end;
          exitOffset = promoteThroughGotoChain(flow, exitOffset);
          if (!exitPoints.contains(exitOffset)) {
            exitPoints.add(exitOffset);
          }
        }
      }

      @Override
      public Void getResult() {
        return null;
      }
    };
    depthFirstSearch(flow, visitor, start, end);
    return exitStatements;
  }

  @SafeVarargs
  private static void processGoto(@NotNull ControlFlow flow, int start, int end,
                                  @NotNull IntList exitPoints,
                                  @NotNull Collection<? super PsiStatement> exitStatements,
                                  @NotNull BranchingInstruction instruction,
                                  PsiStatement statement, Class<? extends PsiStatement> @NotNull ... classesFilter) {
    if (statement == null) return;
    int gotoOffset = instruction.offset;
    if (start > gotoOffset || gotoOffset >= end || isElementOfClass(statement, classesFilter)) {
      // process chain of goto's
      gotoOffset = promoteThroughGotoChain(flow, gotoOffset);

      if (gotoOffset > 0 && (gotoOffset >= end || gotoOffset < start) && !exitPoints.contains(gotoOffset)) {
        exitPoints.add(gotoOffset);
      }
      if (gotoOffset >= end || gotoOffset < start) {
        processGotoStatement(exitStatements, statement, classesFilter);
      }
      else {
        boolean isReturn = instruction instanceof GoToInstruction && ((GoToInstruction)instruction).isReturn;
        final Instruction gotoInstruction = flow.getInstructions().get(gotoOffset);
        isReturn |= gotoInstruction instanceof GoToInstruction && ((GoToInstruction)gotoInstruction).isReturn;
        if (isReturn) {
          processGotoStatement(exitStatements, statement, classesFilter);
        }
      }
    }
  }

  @SafeVarargs
  private static void processGotoStatement(@NotNull Collection<? super PsiStatement> exitStatements,
                                           PsiStatement statement, Class<? extends PsiStatement> @NotNull ... classesFilter) {
    if (statement != null && isElementOfClass(statement, classesFilter)) {
      exitStatements.add(statement);
    }
  }

  @SafeVarargs
  private static boolean isElementOfClass(@NotNull PsiElement element, Class<? extends PsiStatement> @NotNull ... classesFilter) {
    for (Class<? extends PsiStatement> aClassesFilter : classesFilter) {
      if (ReflectionUtil.isAssignable(aClassesFilter, element.getClass())) {
        return true;
      }
    }
    return false;
  }

  private static int promoteThroughGotoChain(@NotNull ControlFlow flow, int offset) {
    List<Instruction> instructions = flow.getInstructions();
    while (true) {
      if (offset >= instructions.size()) break;
      Instruction instruction = instructions.get(offset);
      if (!(instruction instanceof GoToInstruction) || ((GoToInstruction)instruction).isReturn) break;
      offset = ((BranchingInstruction)instruction).offset;
    }
    return offset;
  }

  @SuppressWarnings("unchecked")
  public static final Class<? extends PsiStatement>[] DEFAULT_EXIT_STATEMENTS_CLASSES =
    new Class[]{PsiReturnStatement.class, PsiBreakStatement.class, PsiContinueStatement.class};

  private static PsiStatement findStatement(@NotNull ControlFlow flow, int offset) {
    PsiElement element = flow.getElement(offset);
    return PsiTreeUtil.getParentOfType(element, PsiStatement.class, false);
  }

  /**
   * Detect throw instructions which might affect observable control flow via side effects with local variables.
   * <p>
   * The side effect of exception thrown occurs when a local variable is written in the try block and then accessed
   * in the {@code finally} section or in/after a catch section.
   * <p>
   * Example:
   * <pre>
   * { // --- start of theOuterBlock ---
   *   Status status = STARTED;
   *   try { // --- start of theTryBlock ---
   *     status = PREPARING;
   *     doPrepare(); // may throw exception
   *     status = WORKING;
   *     doWork(); // may throw exception
   *     status = FINISHED;
   *   } // --- end of theTryBlock ---
   *   catch (Exception e) {
   *      // can get PREPARING or WORKING here
   *      LOG.error("Failed when " + status, e); 
   *   }
   *   // can get PREPARING or WORKING here in the case of exception
   *   if (status == FINISHED) {
   *     LOG.info("Finished");
   *   }
   * } // --- end of theOuterBlock ---
   * </pre>
   * In the example above {@code hasObservableThrowExitPoints(theTryBlock) == true},
   * because the resulting value of the "status" variable depends on the exceptions being thrown.
   * In the same example {@code hasObservableThrowExitPoints(theOuterBlock) == false},
   * because no outgoing variables here depend on the exceptions being thrown.
   */
  public static boolean hasObservableThrowExitPoints(@NotNull ControlFlow flow,
                                                     int flowStart,
                                                     int flowEnd,
                                                     PsiElement @NotNull [] elements,
                                                     @NotNull PsiElement enclosingCodeFragment) {
    final List<Instruction> instructions = flow.getInstructions();
    class Worker {
      private @NotNull Map<PsiVariable, IntList> getWritesOffsets() {
        final Map<PsiVariable, IntList> writeOffsets = new HashMap<>();
        for (int i = flowStart; i < flowEnd; i++) {
          Instruction instruction = instructions.get(i);
          if (instruction instanceof WriteVariableInstruction) {
            final PsiVariable variable = ((WriteVariableInstruction)instruction).variable;
            if (variable instanceof PsiLocalVariable || variable instanceof PsiParameter) {
              writeOffsets.computeIfAbsent(variable, k -> new IntArrayList()).add(i);
            }
          }
        }
        LOG.debug("writeOffsets:", writeOffsets);
        return writeOffsets;
      }

      private @NotNull Map<PsiVariable, IntList> getVisibleReadsOffsets(@NotNull Map<PsiVariable, IntList> writeOffsets, @NotNull PsiCodeBlock tryBlock) {
        final Map<PsiVariable, IntList> visibleReadOffsets = new HashMap<>();
        for (PsiVariable variable : writeOffsets.keySet()) {
          if (!PsiTreeUtil.isAncestor(tryBlock, variable, true)) {
            visibleReadOffsets.put(variable, new IntArrayList());
          }
        }
        if (visibleReadOffsets.isEmpty()) return visibleReadOffsets;

        for (int i = 0; i < instructions.size(); i++) {
          final Instruction instruction = instructions.get(i);
          if (instruction instanceof ReadVariableInstruction) {
            final PsiVariable variable = ((ReadVariableInstruction)instruction).variable;
            final IntList readOffsets = visibleReadOffsets.get(variable);
            if (readOffsets != null) {
              readOffsets.add(i);
            }
          }
        }
        LOG.debug("visibleReadOffsets:", visibleReadOffsets);
        return visibleReadOffsets;
      }

      private @NotNull Map<PsiVariable, Set<PsiElement>> getReachableAfterWrite(@NotNull Map<PsiVariable, IntList> writeOffsets,
                                                                                @NotNull Map<PsiVariable, IntList> visibleReadOffsets) {
        final Map<PsiVariable, Set<PsiElement>> afterWrite = new HashMap<>();
        final IntFunction<BitSet> calculator = getReachableInstructionsCalculator();
        for (PsiVariable variable : visibleReadOffsets.keySet()) {
          final BitSet collectedOffsets = new BitSet(flowEnd);
          for (int writeOffset : writeOffsets.get(variable).toIntArray()) {
            LOG.assertTrue(writeOffset >= flowStart, "writeOffset");
            final BitSet reachableOffsets = calculator.apply(writeOffset);
            //skip current assignment
            reachableOffsets.set(writeOffset, false);
            if (writeOffset + 1 < flow.getSize()) {
              final PsiElement nextOffsetElement = flow.getElement(writeOffset + 1);
              if (nextOffsetElement instanceof PsiExpressionStatement) {
                if (flow.getElement(writeOffset) == ((PsiExpressionStatement)nextOffsetElement).getExpression()) {
                  reachableOffsets.set(writeOffset + 1, false);
                }
              }
            }
            collectedOffsets.or(reachableOffsets);
          }
          Set<PsiElement> throwSources = afterWrite.getOrDefault(variable, new HashSet<>());
          for (int i = flowStart; i < flowEnd; i++) {
            if (collectedOffsets.get(i)) {
              throwSources.add(flow.getElement(i));
            }
          }
          final List<PsiElement> subordinates = new ArrayList<>();
          for (PsiElement element : throwSources) {
            if (throwSources.contains(element.getParent())) {
              subordinates.add(element);
            }
          }
          subordinates.forEach(throwSources::remove);
          if (throwSources.isEmpty()) {
            afterWrite.remove(variable);
          } else {
            afterWrite.put(variable, throwSources);
          }
        }
        LOG.debug("afterWrite:", afterWrite);
        return afterWrite;
      }

      private @NotNull IntList getCatchOrFinallyOffsets(@NotNull List<? extends PsiTryStatement> tryStatements, @NotNull List<? extends PsiClassType> thrownExceptions) {
        final IntList catchOrFinallyOffsets = new IntArrayList();
        for (PsiTryStatement tryStatement : tryStatements) {
          final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
          if (finallyBlock != null) {
            int offset = flow.getStartOffset(finallyBlock);
            if (offset >= 0) {
              catchOrFinallyOffsets.add(offset - 2); // -2 is an adjustment for rethrow-after-finally
            }
          }
          for (PsiCatchSection catchSection : tryStatement.getCatchSections()) {
            final PsiCodeBlock catchBlock = catchSection.getCatchBlock();
            final PsiParameter parameter = catchSection.getParameter();
            if (catchBlock != null && parameter != null) {
              for (PsiClassType throwType : thrownExceptions) {
                if (isCaughtExceptionType(throwType, parameter.getType())) {
                  int offset = flow.getStartOffset(catchBlock);
                  if (offset >= 0) {
                    catchOrFinallyOffsets.add(offset - 1); // -1 is an adjustment for catch block initialization
                  }
                }
              }
            }
          }
        }
        return catchOrFinallyOffsets;
      }

      private boolean isAnyReadOffsetReachableFrom(@Nullable IntList readOffsets, @NotNull IntList fromOffsets) {
        if (readOffsets != null && !readOffsets.isEmpty()) {
          final int[] readOffsetsArray = readOffsets.toIntArray();
          for (int j = 0; j < fromOffsets.size(); j++) {
            int fromOffset = fromOffsets.getInt(j);
            if (areInstructionsReachable(flow, readOffsetsArray, fromOffset)) {
              LOG.debug("reachableFromOffset:", fromOffset);
              return true;
            }
          }
        }
        return false;
      }

      private @NotNull IntFunction<BitSet> getReachableInstructionsCalculator() {
        final ControlFlowGraph graph = new ControlFlowGraph(flow.getSize()) {
          @Override
          void addArc(int offset, int nextOffset) {
            nextOffset = promoteThroughGotoChain(flow, nextOffset);
            if (nextOffset >= flowStart && nextOffset < flowEnd) {
              super.addArc(offset, nextOffset);
            }
          }
        };
        graph.buildFrom(flow);

        return startOffset -> {
          BitSet visitedOffsets = new BitSet(flowEnd);
          graph.depthFirstSearch(startOffset, visitedOffsets);
          return visitedOffsets;
        };
      }
    }

    final Worker worker = new Worker();
    final Map<PsiVariable, IntList> writeOffsets = worker.getWritesOffsets();
    if (writeOffsets.isEmpty()) return false;

    final PsiElement commonParent = elements.length != 1 ? PsiTreeUtil.findCommonParent(elements) : elements[0].getParent();
    final List<PsiTryStatement> tryStatements = collectTryStatementStack(commonParent, enclosingCodeFragment);
    if (tryStatements.isEmpty()) return false;
    final PsiCodeBlock tryBlock = tryStatements.get(0).getTryBlock();
    if (tryBlock == null) return false;

    final Map<PsiVariable, IntList> visibleReadOffsets = worker.getVisibleReadsOffsets(writeOffsets, tryBlock);
    if (visibleReadOffsets.isEmpty()) return false;

    final Map<PsiVariable, Set<PsiElement>> afterWrite = worker.getReachableAfterWrite(writeOffsets, visibleReadOffsets);
    if (afterWrite.isEmpty()) return false;

    final PsiClassType runtimeException = PsiType.getJavaLangRuntimeException(tryBlock.getManager(), tryBlock.getResolveScope());
    final boolean runtimeExceptionIsCaught = ContainerUtil.
      exists(tryStatements, (tryStatement) -> isExceptionCaught(tryStatement, runtimeException));

    for (Map.Entry<PsiVariable, Set<PsiElement>> entry : afterWrite.entrySet()) {
      final PsiVariable variable = entry.getKey();
      final PsiElement[] psiElements = entry.getValue().toArray(PsiElement.EMPTY_ARRAY);
      final List<PsiClassType> thrownExceptions = ExceptionUtil.getThrownExceptions(psiElements);
      if (runtimeExceptionIsCaught) {
        thrownExceptions.add(runtimeException);
      }
      if (!thrownExceptions.isEmpty()) {
        final IntList catchOrFinallyOffsets = worker.getCatchOrFinallyOffsets(tryStatements, thrownExceptions);
        if (worker.isAnyReadOffsetReachableFrom(visibleReadOffsets.get(variable), catchOrFinallyOffsets)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean isExceptionCaught(@NotNull PsiTryStatement tryStatement, @NotNull PsiClassType exceptionType) {
    return ContainerUtil.exists(tryStatement.getCatchBlockParameters(), (parameter) -> exceptionType.isAssignableFrom(exceptionType));
  }

  private static @Nullable PsiTryStatement getEnclosingTryStatementHavingCatchOrFinally(@Nullable PsiElement startElement,
                                                                                        @NotNull PsiElement enclosingCodeFragment) {
    for (PsiElement element = startElement; element != null && element != enclosingCodeFragment; element = element.getParent()) {
      if (element instanceof PsiCodeBlock) {
        final PsiElement parent = element.getParent();
        if (parent instanceof PsiTryStatement) {
          final PsiTryStatement tryStatement = (PsiTryStatement)parent;
          if (tryStatement.getTryBlock() == element &&
              (tryStatement.getFinallyBlock() != null || tryStatement.getCatchBlocks().length != 0)) {
            return tryStatement;
          }
        }
      }
    }
    return null;
  }

  private static @NotNull List<PsiTryStatement> collectTryStatementStack(@Nullable PsiElement startElement,
                                                                         @NotNull PsiElement enclosingCodeFragment) {
    final List<PsiTryStatement> stack = new ArrayList<>();
    for (PsiTryStatement tryStatement = getEnclosingTryStatementHavingCatchOrFinally(startElement, enclosingCodeFragment);
         tryStatement != null;
         tryStatement = getEnclosingTryStatementHavingCatchOrFinally(tryStatement, enclosingCodeFragment)) {
      stack.add(tryStatement);
    }
    return stack;
  }

  public static @NotNull PsiElement findCodeFragment(@NotNull PsiElement element) {
    PsiElement codeFragment = element;
    PsiElement parent = codeFragment.getParent();
    while (parent != null) {
      if (parent instanceof PsiDirectory
          || parent instanceof PsiMethod
          || parent instanceof PsiField || parent instanceof PsiClassInitializer
          || parent instanceof DummyHolder
          || parent instanceof PsiLambdaExpression) {
        break;
      }
      codeFragment = parent;
      parent = parent.getParent();
    }
    return codeFragment;
  }

  private static boolean checkReferenceExpressionScope(@NotNull PsiReferenceExpression ref, @NotNull PsiElement targetClassMember) {
    final JavaResolveResult resolveResult = ref.advancedResolve(false);
    final PsiElement def = resolveResult.getElement();
    if (def != null) {
      PsiElement parent = def.getParent();
      PsiElement commonParent = parent == null ? null : PsiTreeUtil.findCommonParent(parent, targetClassMember);
      if (commonParent == null) {
        parent = resolveResult.getCurrentFileResolveScope();
      }
      if (parent instanceof PsiClass) {
        final PsiClass psiClass = (PsiClass)parent;
        if (PsiTreeUtil.isAncestor(targetClassMember, psiClass, false)) return false;
        PsiClass containingClass = PsiTreeUtil.getParentOfType(ref, PsiClass.class);
        while (containingClass != null) {
          if (containingClass.isInheritor(psiClass, true) &&
              PsiTreeUtil.isAncestor(targetClassMember, containingClass, false)) {
            return false;
          }
          containingClass = containingClass.getContainingClass();
        }
      }
    }

    return true;
  }

  /**
   * Checks possibility of extracting code fragment outside containing anonymous (local) class.
   * Also collects variables to be passed as additional parameters.
   *
   * @param array             Vector to collect variables to be passed as additional parameters
   * @param scope             scope to be scanned (part of code fragment to be extracted)
   * @param member            member containing the code to be extracted
   * @param targetClassMember member in target class containing code fragment
   * @return true if a code fragment can be extracted outside
   */
  public static boolean collectOuterLocals(@NotNull List<? super PsiVariable> array, @NotNull PsiElement scope, @NotNull PsiElement member,
                                           @NotNull PsiElement targetClassMember) {
    if (scope instanceof PsiMethodCallExpression) {
      final PsiMethodCallExpression call = (PsiMethodCallExpression)scope;
      if (!checkReferenceExpressionScope(call.getMethodExpression(), targetClassMember)) {
        return false;
      }
    }
    else if (scope instanceof PsiReferenceExpression) {
      if (!checkReferenceExpressionScope((PsiReferenceExpression)scope, targetClassMember)) {
        return false;
      }
    }

    if (scope instanceof PsiJavaCodeReferenceElement) {

      final PsiJavaCodeReferenceElement ref = (PsiJavaCodeReferenceElement)scope;
      final JavaResolveResult result = ref.advancedResolve(false);
      final PsiElement refElement = result.getElement();

      if (refElement != null) {

        PsiElement parent = refElement.getParent();
        parent = parent != null ? PsiTreeUtil.findCommonParent(parent, member) : null;
        if (parent == null) {
          parent = result.getCurrentFileResolveScope();
        }

        if (parent != null && !member.equals(parent)) { // not local in member
          parent = PsiTreeUtil.findCommonParent(parent, targetClassMember);
          if (targetClassMember.equals(parent)) { //something in anonymous class
            if (refElement instanceof PsiVariable) {
              if (scope instanceof PsiReferenceExpression &&
                  PsiUtil.isAccessedForWriting((PsiReferenceExpression)scope)) {
                return false;
              }
              PsiVariable variable = (PsiVariable)refElement;
              if (!array.contains(variable)) {
                array.add(variable);
              }
            }
            else {
              return false;
            }
          }
        }
      }
    }
    else if (scope instanceof PsiThisExpression) {
      PsiJavaCodeReferenceElement qualifier = ((PsiThisExpression)scope).getQualifier();
      if (qualifier == null) {
        return false;
      }
    }
    else if (scope instanceof PsiSuperExpression) {
      if (((PsiSuperExpression)scope).getQualifier() == null) {
        return false;
      }
    }

    for (PsiElement child = scope.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (!collectOuterLocals(array, child, member, targetClassMember)) return false;
    }
    return true;
  }


  /**
   * @return true if each control flow path results in reaching a return statement or exception thrown
   */
  public static boolean returnPresent(@NotNull ControlFlow flow) {
    InstructionClientVisitor<Boolean> visitor = new ReturnPresentClientVisitor(flow);

    depthFirstSearch(flow, visitor);
    return visitor.getResult();
  }

  public static boolean processReturns(@NotNull ControlFlow flow, @NotNull ReturnStatementsVisitor afterVisitor) throws IncorrectOperationException {
    final ConvertReturnClientVisitor instructionsVisitor = new ConvertReturnClientVisitor(flow, afterVisitor);

    depthFirstSearch(flow, instructionsVisitor);

    instructionsVisitor.afterProcessing();
    return instructionsVisitor.getResult();
  }

  private static class ConvertReturnClientVisitor extends ReturnPresentClientVisitor {
    private final List<PsiReturnStatement> myAffectedReturns;
    private final ReturnStatementsVisitor myVisitor;

    ConvertReturnClientVisitor(@NotNull ControlFlow flow, @NotNull ReturnStatementsVisitor visitor) {
      super(flow);
      myAffectedReturns = new ArrayList<>();
      myVisitor = visitor;
    }

    @Override
    public void visitGoToInstruction(GoToInstruction instruction, int offset, int nextOffset) {
      super.visitGoToInstruction(instruction, offset, nextOffset);

      if (instruction.isReturn) {
        final PsiElement element = myFlow.getElement(offset);
        if (element instanceof PsiReturnStatement) {
          final PsiReturnStatement returnStatement = (PsiReturnStatement)element;
          myAffectedReturns.add(returnStatement);
        }
      }
    }

    void afterProcessing() throws IncorrectOperationException {
      myVisitor.visit(myAffectedReturns);
    }
  }

  private static class ReturnPresentClientVisitor extends InstructionClientVisitor<Boolean> {
    // false if control flow at this offset terminates either by return called or exception thrown
    private final boolean[] isNormalCompletion;
    protected final ControlFlow myFlow;

    ReturnPresentClientVisitor(@NotNull ControlFlow flow) {
      myFlow = flow;
      isNormalCompletion = new boolean[myFlow.getSize() + 1];
      isNormalCompletion[myFlow.getSize()] = true;
    }

    @Override
    public void visitConditionalThrowToInstruction(ConditionalThrowToInstruction instruction, int offset, int nextOffset) {
      if (nextOffset > myFlow.getSize()) nextOffset = myFlow.getSize();
      boolean isNormal = instruction.offset == nextOffset && nextOffset != offset + 1 ?
                         !isLeaf(nextOffset) && isNormalCompletion[nextOffset] :
                         isLeaf(nextOffset) || isNormalCompletion[nextOffset];

      isNormalCompletion[offset] |= isNormal;
    }

    @Override
    public void visitThrowToInstruction(ThrowToInstruction instruction, int offset, int nextOffset) {
      if (nextOffset > myFlow.getSize()) nextOffset = myFlow.getSize();
      isNormalCompletion[offset] |= !isLeaf(nextOffset) && isNormalCompletion[nextOffset];
    }

    @Override
    public void visitGoToInstruction(GoToInstruction instruction, int offset, int nextOffset) {
      if (nextOffset > myFlow.getSize()) nextOffset = myFlow.getSize();
      isNormalCompletion[offset] |= !instruction.isReturn && isNormalCompletion[nextOffset];
    }

    @Override
    public void visitInstruction(Instruction instruction, int offset, int nextOffset) {
      if (nextOffset > myFlow.getSize()) nextOffset = myFlow.getSize();

      boolean isNormal = isLeaf(nextOffset) || isNormalCompletion[nextOffset];
      isNormalCompletion[offset] |= isNormal;
    }

    @Override
    public @NotNull Boolean getResult() {
      return !isNormalCompletion[0];
    }
  }

  public static boolean returnPresentBetween(@NotNull ControlFlow flow, int startOffset, int endOffset) {
    final class MyVisitor extends InstructionClientVisitor<Boolean> {
      // false if control flow at this offset terminates either by return called or exception thrown
      private final boolean[] isNormalCompletion = new boolean[flow.getSize() + 1];

      private MyVisitor() {
        int i;
        final int length = flow.getSize();
        for (i = 0; i < startOffset; i++) {
          isNormalCompletion[i] = true;
        }
        for (i = endOffset; i <= length; i++) {
          isNormalCompletion[i] = true;
        }
      }

      @Override
      public void visitConditionalThrowToInstruction(ConditionalThrowToInstruction instruction, int offset, int nextOffset) {
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();
        if (offset > endOffset) return;
        int throwToOffset = instruction.offset;
        boolean isNormal;
        if (throwToOffset == nextOffset) {
          if (throwToOffset <= endOffset) {
            isNormal = !isLeaf(nextOffset) && isNormalCompletion[nextOffset];
          }
          else {
            return;
          }
        }
        else {
          isNormal = isLeaf(nextOffset) || isNormalCompletion[nextOffset];
        }
        isNormalCompletion[offset] |= isNormal;
      }

      @Override
      public void visitThrowToInstruction(ThrowToInstruction instruction, int offset, int nextOffset) {
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();
        if (offset > endOffset) return;
        if (nextOffset <= endOffset) {
          boolean isNormal = !isLeaf(nextOffset) && isNormalCompletion[nextOffset];
          isNormalCompletion[offset] |= isNormal;
        }
      }

      @Override
      public void visitCallInstruction(CallInstruction instruction, int offset, int nextOffset) {
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();
        if (offset > endOffset) return;
        if (nextOffset > endOffset && nextOffset != offset + 1) {
          return;
        }
        boolean isNormal = isNormalCompletion[nextOffset];
        isNormalCompletion[offset] |= isNormal;
      }

      @Override
      public void visitGoToInstruction(GoToInstruction instruction, int offset, int nextOffset) {
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();
        if (offset > endOffset) return;
        boolean isRethrowFromFinally = instruction instanceof ReturnInstruction && ((ReturnInstruction)instruction).isRethrowFromFinally();
        boolean isNormal = !instruction.isReturn && isNormalCompletion[nextOffset] && !isRethrowFromFinally;
        isNormalCompletion[offset] |= isNormal;
      }

      @Override
      public void visitInstruction(Instruction instruction, int offset, int nextOffset) {
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();
        if (offset > endOffset) return;
        final boolean isNormal = isLeaf(nextOffset) || isNormalCompletion[nextOffset];
        isNormalCompletion[offset] |= isNormal;
      }

      @Override
      public @NotNull Boolean getResult() {
        return !isNormalCompletion[startOffset];
      }
    }
    final MyVisitor visitor = new MyVisitor();
    depthFirstSearch(flow, visitor, startOffset, endOffset);
    return visitor.getResult();
  }

  /**
   * Returns true iff exists control flow path completing normally, i.e. not resulting in return,break,continue or exception thrown.
   * In other words, if we add instruction after controlflow specified, it should be reachable
   */
  public static boolean canCompleteNormally(@NotNull ControlFlow flow, int startOffset, int endOffset) {
    class MyVisitor extends InstructionClientVisitor<Boolean> {
      // false if control flow at this offset terminates abruptly
      private final boolean[] canCompleteNormally = new boolean[flow.getSize() + 1];

      @Override
      public void visitConditionalGoToInstruction(ConditionalGoToInstruction instruction, int offset, int nextOffset) {
        checkInstruction(offset, nextOffset, false);
      }

      @Override
      public void visitGoToInstruction(GoToInstruction instruction, int offset, int nextOffset) {
        checkInstruction(offset, nextOffset, instruction.isReturn);
      }

      private void checkInstruction(int offset, int nextOffset, boolean isReturn) {
        if (offset > endOffset) return;
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();
        boolean isNormal = nextOffset <= endOffset && !isReturn && (nextOffset == endOffset || canCompleteNormally[nextOffset]);
        if (isNormal && nextOffset == endOffset) {
          PsiElement element = flow.getElement(offset);
          if (element instanceof PsiBreakStatement) {
            PsiStatement exitedStatement = ((PsiBreakStatement)element).findExitedStatement();
            if (exitedStatement == null || flow.getStartOffset(exitedStatement) < startOffset) {
              isNormal = false;
            }
          }
          else if (element instanceof PsiYieldStatement) {
            PsiSwitchExpression exitedSwitch = ((PsiYieldStatement)element).findEnclosingExpression();
            if (exitedSwitch == null || flow.getStartOffset(exitedSwitch) < startOffset) {
              isNormal = false;
            }
          }
          else if (element instanceof PsiContinueStatement) {
            PsiStatement continuedStatement = ((PsiContinueStatement)element).findContinuedStatement();
            if (continuedStatement == null || flow.getStartOffset(continuedStatement) < startOffset) {
              isNormal = false;
            }
          }
        }
        canCompleteNormally[offset] |= isNormal;
      }

      @Override
      public void visitConditionalThrowToInstruction(ConditionalThrowToInstruction instruction, int offset, int nextOffset) {
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();
        if (offset > endOffset) return;
        int throwToOffset = instruction.offset;
        boolean isNormal;
        //if it is not PsiThrowStatement and the next step is the last step,
        //it is possible to complete it normally.
        if (throwToOffset == nextOffset && nextOffset == endOffset &&
            offset + 1 == nextOffset && !(flow.getElement(offset) instanceof PsiThrowStatement)) {
          isNormal = true;
        }
        else if (throwToOffset == nextOffset) {
          isNormal = throwToOffset <= endOffset && !isLeaf(nextOffset) && canCompleteNormally[nextOffset];
        }
        else {
          //if the next step is the last step, it is certainly completed normally in `not-throw` branch
          //otherwise, take completion from the next offset
          isNormal = nextOffset == endOffset || canCompleteNormally[nextOffset];
        }
        canCompleteNormally[offset] |= isNormal;
      }

      @Override
      public void visitThrowToInstruction(ThrowToInstruction instruction, int offset, int nextOffset) {
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();
        if (offset > endOffset) return;
        if (nextOffset <= endOffset) {
          boolean isNormal = !isLeaf(nextOffset) && canCompleteNormally[nextOffset];
          canCompleteNormally[offset] |= isNormal;
        }
      }

      @Override
      public void visitCallInstruction(CallInstruction instruction, int offset, int nextOffset) {
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();
        if (offset > endOffset) return;
        if (nextOffset > endOffset && nextOffset != offset + 1) {
          return;
        }
        boolean isNormal = canCompleteNormally[nextOffset];
        canCompleteNormally[offset] |= isNormal;
      }

      @Override
      public void visitInstruction(Instruction instruction, int offset, int nextOffset) {
        checkInstruction(offset, nextOffset, false);
      }

      @Override
      public @NotNull Boolean getResult() {
        return canCompleteNormally[startOffset];
      }
    }
    final MyVisitor visitor = new MyVisitor();
    depthFirstSearch(flow, visitor, startOffset, endOffset);
    return visitor.getResult();
  }

  /**
   * @return any unreachable statement or null
   */
  public static PsiElement getUnreachableStatement(@NotNull ControlFlow flow) {
    final InstructionClientVisitor<PsiElement> visitor = new UnreachableStatementClientVisitor(flow);
    depthFirstSearch(flow, visitor);
    return visitor.getResult();
  }

  private static class UnreachableStatementClientVisitor extends InstructionClientVisitor<PsiElement> {
    private final ControlFlow myFlow;

    UnreachableStatementClientVisitor(@NotNull ControlFlow flow) {
      myFlow = flow;
    }

    @Override
    public PsiElement getResult() {
      for (int i = 0; i < processedInstructions.length; i++) {
        if (!processedInstructions[i]) {
          PsiElement element = myFlow.getElement(i);

          final PsiElement unreachableParent = getUnreachableExpressionParent(element);
          if (unreachableParent != null) {
            return correctUnreachableStatement(unreachableParent);
          }

          if (element == null || !PsiUtil.isStatement(element)) continue;
          if (element.getParent() instanceof PsiExpression) continue;

          // ignore for(;;) statement unreachable update
          while (element instanceof PsiExpression) {
            element = element.getParent();
          }
          if (element instanceof PsiStatement
              && element.getParent() instanceof PsiForStatement
              && element == ((PsiForStatement)element.getParent()).getUpdate()) {
            continue;
          }
          //filter out generated statements
          final int endOffset = myFlow.getEndOffset(element);
          if (endOffset != i + 1) continue;
          final int startOffset = myFlow.getStartOffset(element);
          // this offset actually is a part of reachable statement
          if (0 <= startOffset && startOffset < processedInstructions.length && processedInstructions[startOffset]) continue;
          final PsiElement enclosingStatement = getEnclosingUnreachableStatement(element);
          return enclosingStatement != null ? enclosingStatement : element;
        }
      }
      return null;
    }

    private static PsiElement correctUnreachableStatement(PsiElement statement) {
      if (!(statement instanceof PsiStatement)) return statement;
      while (true) {
        PsiElement parent = statement.getParent();
        if (parent instanceof PsiDoWhileStatement || parent instanceof PsiLabeledStatement) {
          statement = parent;
          continue;
        }
        if (parent instanceof PsiCodeBlock && PsiTreeUtil.getPrevSiblingOfType(statement, PsiStatement.class) == null) {
          PsiElement grandParent = parent.getParent();
          if (grandParent instanceof PsiBlockStatement) {
            statement = grandParent;
            continue;
          }
        }
        return statement;
      }
    }

    private static @Nullable PsiElement getUnreachableExpressionParent(@Nullable PsiElement element) {
      if (element instanceof PsiExpression) {
        PsiElement expression = PsiTreeUtil.findFirstParent(element, e -> !(e.getParent() instanceof PsiParenthesizedExpression));
        while (expression != null) {
          final PsiElement parent = expression.getParent();
          if (parent instanceof PsiExpressionStatement) {
            final PsiElement grandParent = parent.getParent();
            if (grandParent instanceof PsiForStatement) {
              if (((PsiForStatement)grandParent).getInitialization() == parent) {
                return grandParent;
              }
              return null;
            }
            return parent;
          }
          if (parent instanceof PsiLocalVariable && ((PsiLocalVariable)parent).getInitializer() == expression) {
            PsiElement grandParent = parent.getParent();
            if (grandParent instanceof PsiDeclarationStatement) return grandParent;
            if (grandParent instanceof PsiResourceList && grandParent.getParent() instanceof PsiTryStatement) {
              return grandParent.getParent();
            }
            return null;
          }
          if (parent instanceof PsiIfStatement && ((PsiIfStatement)parent).getCondition() == expression ||
              parent instanceof PsiSwitchBlock && ((PsiSwitchBlock)parent).getExpression() == expression ||
              parent instanceof PsiWhileStatement && ((PsiWhileStatement)parent).getCondition() == expression ||
              parent instanceof PsiForeachStatement && ((PsiForeachStatement)parent).getIteratedValue() == expression ||
              parent instanceof PsiReturnStatement && ((PsiReturnStatement)parent).getReturnValue() == expression ||
              parent instanceof PsiYieldStatement && ((PsiYieldStatement)parent).getExpression() == expression ||
              parent instanceof PsiThrowStatement && ((PsiThrowStatement)parent).getException() == expression ||
              parent instanceof PsiSynchronizedStatement && ((PsiSynchronizedStatement)parent).getLockExpression() == expression ||
              parent instanceof PsiAssertStatement && ((PsiAssertStatement)parent).getAssertCondition() == expression) {
            return parent;
          }
          if (parent instanceof PsiExpression) {
            expression = parent;
          } else {
            break;
          }
        }
      }
      return null;
    }

    private static @Nullable PsiElement getEnclosingUnreachableStatement(@NotNull PsiElement statement) {
      final PsiElement parent = statement.getParent();
      if (parent instanceof PsiDoWhileStatement && ((PsiDoWhileStatement)parent).getBody() == statement) {
        return parent;
      }
      if (parent instanceof PsiCodeBlock && PsiTreeUtil.getNextSiblingOfType(parent.getFirstChild(), PsiStatement.class) == statement) {
        final PsiBlockStatement blockStatement = ObjectUtils.tryCast(parent.getParent(), PsiBlockStatement.class);
        if (blockStatement != null) {
          final PsiElement blockParent = blockStatement.getParent();
          if (blockParent instanceof PsiDoWhileStatement && ((PsiDoWhileStatement)blockParent).getBody() == blockStatement) {
            return blockParent;
          }
        }
      }
      return getUnreachableStatementParent(statement);
    }

    private static @Nullable PsiElement getUnreachableStatementParent(@NotNull PsiElement statement) {
      final PsiElement parent = statement.getParent();
      if (parent instanceof PsiForStatement && ((PsiForStatement)parent).getInitialization() == statement) {
        return parent;
      }
      return null;
    }
  }

  private static PsiReferenceExpression getEnclosingReferenceExpression(@NotNull PsiElement element, @NotNull PsiVariable variable) {
    final PsiReferenceExpression reference = findReferenceTo(element, variable);
    if (reference != null) return reference;
    while (element != null) {
      if (element instanceof PsiReferenceExpression) {
        return (PsiReferenceExpression)element;
      }
      if (element instanceof PsiMethod || element instanceof PsiClass) {
        return null;
      }
      element = element.getParent();
    }
    return null;
  }

  private static PsiReferenceExpression findReferenceTo(@NotNull PsiElement element, @NotNull PsiVariable variable) {
    if (element instanceof PsiReferenceExpression
        && ExpressionUtil.isEffectivelyUnqualified((PsiReferenceExpression)element)
        && ((PsiReferenceExpression)element).isReferenceTo(variable)) {
      return (PsiReferenceExpression)element;
    }
    for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      final PsiReferenceExpression reference = findReferenceTo(child, variable);
      if (reference != null) return reference;
    }
    return null;
  }

  /**
   * Returns true of instruction at given offset is a dominator for target instruction (that is: execution from flow start to
   * the target always goes through given offset).
   * @param flow control flow to analyze
   * @param maybeDominator a dominator candidate offset
   * @param target a target instruction offset
   * @return true if instruction at maybeDominator offset is actually a dominator.
   */
  public static boolean isDominator(ControlFlow flow, int maybeDominator, int target) {
    class MyVisitor extends InstructionClientVisitor<Boolean> {
      private final BitSet myReachedWithoutDominator = new BitSet();

      @Override
      public void visitInstruction(Instruction instruction, int offset, int nextOffset) {
        super.visitInstruction(instruction, offset, nextOffset);
        if (nextOffset != maybeDominator && (target == nextOffset || myReachedWithoutDominator.get(nextOffset))) {
          myReachedWithoutDominator.set(offset);
        }
      }

      @Override
      public Boolean getResult() {
        return myReachedWithoutDominator.get(0);
      }
    }
    MyVisitor visitor = new MyVisitor();
    depthFirstSearch(flow, visitor, 0, target);
    return !visitor.getResult();
  }

  public static boolean isVariableDefinitelyAssigned(@NotNull PsiVariable variable, @NotNull ControlFlow flow) {
    PsiElement parent = variable.getParent();
    final int variableDeclarationOffset = parent == null ? -1 : flow.getStartOffset(parent);
    int offset = variableDeclarationOffset > -1 ? variableDeclarationOffset : 0;
    boolean[] unassignedOffsets = getVariablePossiblyUnassignedOffsets(variable, flow);
    return !unassignedOffsets[offset];
  }

  /**
   * Returns offsets starting from which the variable could be unassigned
   *
   * @param variable variable to check
   * @param flow control flow
   * @return a boolean array which values correspond to control flow offset.
   * True value means that variable could be unassigned when execution starts from given offset.
   */
  public static boolean[] getVariablePossiblyUnassignedOffsets(@NotNull PsiVariable variable, @NotNull ControlFlow flow) {
    class MyVisitor extends InstructionClientVisitor<boolean[]> {
      final PsiManager psiManager = variable.getManager();

      // true if from this point below there may be branch with no variable assignment
      private final boolean[] maybeUnassigned = new boolean[flow.getSize() + 1];

      {
        maybeUnassigned[maybeUnassigned.length - 1] = true;
      }

      @Override
      public void visitWriteVariableInstruction(WriteVariableInstruction instruction, int offset, int nextOffset) {
        if (psiManager.areElementsEquivalent(instruction.variable, variable)) {
          maybeUnassigned[offset] = false;
        }
        else {
          visitInstruction(instruction, offset, nextOffset);
        }
      }

      @Override
      public void visitConditionalThrowToInstruction(ConditionalThrowToInstruction instruction, int offset, int nextOffset) {
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();
        boolean unassigned = offset == flow.getSize() - 1
                             || !isLeaf(nextOffset) && maybeUnassigned[nextOffset];

        maybeUnassigned[offset] |= unassigned;
      }

      @Override
      public void visitCallInstruction(CallInstruction instruction, int offset, int nextOffset) {
        visitInstruction(instruction, offset, nextOffset);
        // clear return statements after procedure as well
        for (int i = instruction.procBegin; i < instruction.procEnd + 3; i++) {
          maybeUnassigned[i] = false;
        }
      }

      @Override
      public void visitGoToInstruction(GoToInstruction instruction, int offset, int nextOffset) {
        if (instruction.isReturn && variable instanceof PsiLocalVariable) {
          if (nextOffset > flow.getSize()) nextOffset = flow.getSize();
          boolean unassigned = !isLeaf(nextOffset) && maybeUnassigned[nextOffset];
          maybeUnassigned[offset] |= unassigned;
        }
        else {
          super.visitGoToInstruction(instruction, offset, nextOffset);
        }
      }

      @Override
      public void visitThrowToInstruction(ThrowToInstruction instruction, int offset, int nextOffset) {
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();
        boolean unassigned = !isLeaf(nextOffset) && maybeUnassigned[nextOffset];
        maybeUnassigned[offset] |= unassigned;
      }

      @Override
      public void visitInstruction(Instruction instruction, int offset, int nextOffset) {
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();

        boolean unassigned = isLeaf(nextOffset) || maybeUnassigned[nextOffset];
        maybeUnassigned[offset] |= unassigned;
      }

      @Override
      public boolean @NotNull [] getResult() {
        return maybeUnassigned;
      }
    }
    if (flow.getSize() == 0) return new boolean[] {true};
    MyVisitor visitor = new MyVisitor();
    depthFirstSearch(flow, visitor);
    return visitor.getResult();
  }

  public static boolean isVariableDefinitelyNotAssigned(@NotNull PsiVariable variable, @NotNull ControlFlow flow) {
    class MyVisitor extends InstructionClientVisitor<Boolean> {
      final PsiManager psiManager = variable.getManager();
      // true if from this point below there may be branch with variable assignment
      private final boolean[] maybeAssigned = new boolean[flow.getSize() + 1];

      @Override
      public void visitWriteVariableInstruction(WriteVariableInstruction instruction, int offset, int nextOffset) {
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();
        boolean assigned = psiManager.areElementsEquivalent(instruction.variable, variable) || maybeAssigned[nextOffset];
        maybeAssigned[offset] |= assigned;
      }

      @Override
      public void visitThrowToInstruction(ThrowToInstruction instruction, int offset, int nextOffset) {
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();
        boolean assigned = !isLeaf(nextOffset) && maybeAssigned[nextOffset];
        maybeAssigned[offset] |= assigned;
      }

      @Override
      public void visitConditionalThrowToInstruction(ConditionalThrowToInstruction instruction, int offset, int nextOffset) {
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();
        int throwToOffset = instruction.offset;
        boolean assigned = throwToOffset == nextOffset ? !isLeaf(nextOffset) && maybeAssigned[nextOffset] :
                           maybeAssigned[nextOffset];
        maybeAssigned[offset] |= assigned;
      }

      @Override
      public void visitInstruction(Instruction instruction, int offset, int nextOffset) {
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();

        boolean assigned = maybeAssigned[nextOffset];

        maybeAssigned[offset] |= assigned;
      }

      @Override
      public @NotNull Boolean getResult() {
        return !maybeAssigned[0];
      }
    }
    MyVisitor visitor = new MyVisitor();
    depthFirstSearch(flow, visitor);
    return visitor.getResult();
  }

  /**
   * Returns true if the value the variable has at start is later referenced without going through stop instruction
   *
   * @param flow ControlFlow to analyze
   * @param start the point at which variable value is created
   * @param stop the stop-point
   * @param variable the variable to examine
   * @return true if the value the variable has at start is later referenced without going through stop instruction
   */
  public static boolean isValueUsedWithoutVisitingStop(@NotNull ControlFlow flow, int start, int stop, @NotNull PsiVariable variable) {
    if (start == stop) return false;

    class MyVisitor extends InstructionClientVisitor<Boolean> {
      // true if value the variable has at given offset maybe referenced without going through stop instruction
      private final boolean[] maybeReferenced = new boolean[flow.getSize() + 1];
      final PsiManager psiManager = variable.getManager();

      @Override
      public void visitInstruction(Instruction instruction, int offset, int nextOffset) {
        if (offset == stop) {
          maybeReferenced[offset] = false;
          return;
        }
        if (instruction instanceof WriteVariableInstruction &&
            psiManager.areElementsEquivalent(((WriteVariableInstruction)instruction).variable, variable)) {
          maybeReferenced[offset] = false;
          return;
        }
        if (maybeReferenced[offset]) return;
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();

        boolean nextState = maybeReferenced[nextOffset];
        maybeReferenced[offset] = nextState || instruction instanceof ReadVariableInstruction && 
                                               psiManager.areElementsEquivalent(((ReadVariableInstruction)instruction).variable, variable);
      }

      @Override
      public @NotNull Boolean getResult() {
        return maybeReferenced[start];
      }
    }
    MyVisitor visitor = new MyVisitor();
    depthFirstSearch(flow, visitor, start, flow.getSize());
    return visitor.getResult();
  }

  /**
   * Checks if the control flow instruction at given offset accesses (reads or writes) given variable
   *
   * @param flow control flow
   * @param offset offset inside given control flow
   * @param variable a variable the access to which is to be checked
   * @return true if the given instruction is actually a variable access
   */
  public static boolean isVariableAccess(@NotNull ControlFlow flow, int offset, @NotNull PsiVariable variable) {
    Instruction instruction = flow.getInstructions().get(offset);
    PsiManager psiManager = variable.getManager();
    return instruction instanceof ReadVariableInstruction && 
           psiManager.areElementsEquivalent(((ReadVariableInstruction)instruction).variable, variable) ||
           instruction instanceof WriteVariableInstruction && 
           psiManager.areElementsEquivalent(((WriteVariableInstruction)instruction).variable, variable);
  }

  public static class ControlFlowEdge {
    public final int myFrom;
    public final int myTo;

    ControlFlowEdge(int from, int to) {
      myFrom = from;
      myTo = to;
    }

    @Override
    public String toString() {
      return myFrom+"->"+myTo;
    }
  }

  /**
   * Returns control flow edges which are potentially reachable from start instruction
   *
   * @param flow control flow to analyze
   * @param start starting instruction offset
   * @return a list of edges
   */
  public static @NotNull List<ControlFlowEdge> getEdges(@NotNull ControlFlow flow, int start) {
    final List<ControlFlowEdge> list = new ArrayList<>();
    depthFirstSearch(flow, new InstructionClientVisitor<Void>() {
      @Override
      public void visitInstruction(Instruction instruction, int offset, int nextOffset) {
        list.add(new ControlFlowEdge(offset, nextOffset));
      }

      @Override
      public Void getResult() {
        return null;
      }
    }, start, flow.getSize());
    return list;
  }

  /**
   * @return min offset after sourceOffset which is definitely reachable from all references
   */
  public static int getMinDefinitelyReachedOffset(@NotNull ControlFlow flow, int sourceOffset,
                                                  @NotNull List<? extends PsiElement> references) {
    class MyVisitor extends InstructionClientVisitor<Integer> {
      // set of exit points reached from this offset
      private final IntSet[] exitPoints = new IntOpenHashSet[flow.getSize()];

      @Override
      public void visitInstruction(Instruction instruction, int offset, int nextOffset) {
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();

        if (exitPoints[offset] == null) {
          exitPoints[offset] = new IntOpenHashSet();
        }
        if (isLeaf(nextOffset)) {
          exitPoints[offset].add(offset);
        }
        else if (exitPoints[nextOffset] != null) {
          exitPoints[offset].addAll(exitPoints[nextOffset]);
        }
      }

      @Override
      public @NotNull Integer getResult() {
        int minOffset = flow.getSize();
        int maxExitPoints = 0;
        nextOffset:
        for (int i = sourceOffset; i < exitPoints.length; i++) {
          IntSet exitPointSet = exitPoints[i];
          final int size = exitPointSet == null ? 0 : exitPointSet.size();
          if (size > maxExitPoints) {
            // this offset should be reachable from all other references
            for (PsiElement element : references) {
              final PsiElement statement = PsiUtil.getEnclosingStatement(element);
              if (statement == null) continue;
              final int endOffset = flow.getEndOffset(statement);
              if (endOffset == -1) continue;
              if (i != endOffset && !isInstructionReachable(flow, i, endOffset)) continue nextOffset;
            }
            minOffset = i;
            maxExitPoints = size;
          }
        }
        return minOffset;
      }
    }
    MyVisitor visitor = new MyVisitor();
    depthFirstSearch(flow, visitor);
    return visitor.getResult();
  }

  private static int findUnprocessed(int startOffset, int endOffset, @NotNull InstructionClientVisitor<?> visitor) {
    for (int i = startOffset; i < endOffset; i++) {
      if (!visitor.processedInstructions[i]) {
        return i;
      }
    }
    return endOffset;
  }

  private static void depthFirstSearch(@NotNull ControlFlow flow, @NotNull InstructionClientVisitor<?> visitor) {
    depthFirstSearch(flow, visitor, 0, flow.getSize());
  }

  private static void depthFirstSearch(@NotNull ControlFlow flow, @NotNull InstructionClientVisitor<?> visitor, 
                                       int startOffset, int endOffset) {
    visitor.processedInstructions = new boolean[endOffset];
    internalDepthFirstSearch(flow.getInstructions(), visitor, startOffset, endOffset);
  }

  private static void internalDepthFirstSearch(@NotNull List<? extends Instruction> instructions,
                                               @NotNull InstructionClientVisitor<?> clientVisitor,
                                               int startOffset,
                                               int endOffset) {
    final WalkThroughStack walkThroughStack = new WalkThroughStack(instructions.size() / 2);
    walkThroughStack.push(startOffset);

    ControlFlowInstructionVisitor getNextOffsetVisitor = new ControlFlowInstructionVisitor() {
      @Override
      public void visitCallInstruction(CallInstruction instruction, int offset, int nextOffset) {
        int newOffset = instruction.offset;
        // 'procedure' pointed by call instruction should be processed regardless of whether it was already visited or not
        // clear procedure text and return instructions afterwards
        int i;
        for (i = instruction.procBegin;
             i < clientVisitor.processedInstructions.length &&
             (i < instruction.procEnd || i < instructions.size() && instructions.get(i) instanceof ReturnInstruction); i++) {
          clientVisitor.processedInstructions[i] = false;
        }
        clientVisitor.procedureEntered(instruction.procBegin, i);
        walkThroughStack.currentStack = new CallStackItem(walkThroughStack.currentStack, offset + 1);
        walkThroughStack.push(offset, newOffset);
        walkThroughStack.push(newOffset);
      }

      @Override
      public void visitReturnInstruction(ReturnInstruction instruction, int offset, int nextOffset) {
        int newOffset = -1;
        if (walkThroughStack.currentStack != null) {
          newOffset = walkThroughStack.currentStack.target;
          walkThroughStack.currentStack = walkThroughStack.currentStack.next;
        }
        if (instruction.offset != 0) {
          newOffset = instruction.offset;
        }
        if (newOffset != -1) {
          walkThroughStack.push(offset, newOffset);
          walkThroughStack.push(newOffset);
        }
      }

      @Override
      public void visitBranchingInstruction(BranchingInstruction instruction, int offset, int nextOffset) {
        int newOffset = instruction.offset;
        walkThroughStack.push(offset, newOffset);
        walkThroughStack.push(newOffset);
      }

      @Override
      public void visitConditionalBranchingInstruction(ConditionalBranchingInstruction instruction, int offset, int nextOffset) {
        int newOffset = instruction.offset;

        walkThroughStack.push(offset, newOffset);
        walkThroughStack.push(offset, offset + 1);
        walkThroughStack.push(newOffset);
        walkThroughStack.push(offset + 1);
      }

      @Override
      public void visitInstruction(Instruction instruction, int offset, int nextOffset) {
        int newOffset = offset + 1;
        walkThroughStack.push(offset, newOffset);
        walkThroughStack.push(newOffset);
      }
    };
    while (!walkThroughStack.isEmpty()) {
      final int offset = walkThroughStack.peekOldOffset();
      final int newOffset = walkThroughStack.popNewOffset();

      if (offset >= endOffset) {
        continue;
      }
      Instruction instruction = instructions.get(offset);

      if (clientVisitor.processedInstructions[offset]) {
        if (newOffset != -1) {
          instruction.accept(clientVisitor, offset, newOffset);
        }
        continue;
      }

      clientVisitor.processedInstructions[offset] = true;
      instruction.accept(getNextOffsetVisitor, offset, newOffset);
    }
  }

  private static final class CallStackItem {
    final CallStackItem next;
    final int target;

    private CallStackItem(CallStackItem next, int target) {
      this.next = next;
      this.target = target;
    }
  }

  private static class WalkThroughStack {
    private int[] oldOffsets;
    private int[] newOffsets;
    private CallStackItem[] callStacks;
    private CallStackItem currentStack;
    private int size;

    WalkThroughStack(int initialSize) {
      if (initialSize < 2) initialSize = 2;
      oldOffsets = new int[initialSize];
      newOffsets = new int[initialSize];
      callStacks = new CallStackItem[initialSize];
    }

    /**
     * Push an arc of the graph (oldOffset -> newOffset)
     */
    void push(int oldOffset, int newOffset) {
      LOG.assertTrue(oldOffset >= 0, "negative offset is pushed to walk-through stack");
      if (size >= newOffsets.length) {
        int newSize = size * 3 / 2;
        oldOffsets = Arrays.copyOf(oldOffsets, newSize);
        newOffsets = Arrays.copyOf(newOffsets, newSize);
        callStacks = Arrays.copyOf(callStacks, newSize);
      }
      oldOffsets[size] = oldOffset;
      newOffsets[size] = newOffset;
      callStacks[size] = currentStack;
      size++;
    }

    /**
     * Push a node of the graph. The node is represented as an arc with newOffset==-1
     */
    void push(int offset) {
      push(offset, -1);
    }

    /**
     * Should be used in pair with {@link #popNewOffset()}
     */
    int peekOldOffset() {
      return oldOffsets[size - 1];
    }

    /**
     * Should be used in pair with {@link #peekOldOffset()}
     */
    int popNewOffset() {
      currentStack = callStacks[--size];
      return newOffsets[size];
    }

    boolean isEmpty() {
      return size == 0;
    }

    @Override
    public String toString() {
      StringBuilder s = new StringBuilder();
      for (int i = 0; i < size; i++) {
        if (s.length() != 0) s.append(' ');
        if (newOffsets[i] != -1) {
          s.append('(').append(oldOffsets[i]).append("->").append(newOffsets[i]).append(')');
        }
        else {
          s.append('[').append(oldOffsets[i]).append(']');
        }
      }
      return s.toString();
    }
  }

  private static boolean isInsideReturnStatement(PsiElement element) {
    while (element instanceof PsiExpression) element = element.getParent();
    return element instanceof PsiReturnStatement;
  }

  private static class CopyOnWriteSet {
    private final Set<VariableInfo> set;

    CopyOnWriteSet(@NotNull VariableInfo info) {
      this(Collections.singletonList(info));
    }

    CopyOnWriteSet(@NotNull Collection<? extends VariableInfo> infos) {
      set = new HashSet<>(infos);
    }

    public @NotNull CopyOnWriteSet add(@NotNull VariableInfo value) {
      //if (set.contains(value)) return this;
      CopyOnWriteSet newList = new CopyOnWriteSet(set);
      newList.set.remove(value);
      newList.set.add(value);
      return newList;
    }

    public @NotNull CopyOnWriteSet remove(@NotNull VariableInfo value) {
      if (!set.contains(value)) return this;
      CopyOnWriteSet newList = new CopyOnWriteSet(set);
      newList.set.remove(value);
      return newList;
    }

    public @NotNull Set<VariableInfo> getSet() {
      return set;
    }

    public @NotNull CopyOnWriteSet addAll(@NotNull CopyOnWriteSet addList) {
      Set<VariableInfo> toAdd = addList.getSet();
      if (set.containsAll(toAdd)) return this;
      CopyOnWriteSet newList = new CopyOnWriteSet(set);
      newList.set.addAll(toAdd);
      return newList;
    }

    public static @NotNull CopyOnWriteSet add(@Nullable CopyOnWriteSet list, @NotNull VariableInfo value) {
      return list == null ? new CopyOnWriteSet(value) : list.add(value);
    }
  }

  public static class VariableInfo {
    private final PsiVariable variable;
    public final PsiElement expression;

    public VariableInfo(@NotNull PsiVariable variable, @Nullable PsiElement expression) {
      this.variable = variable;
      this.expression = expression;
    }

    @Override
    public boolean equals(Object o) {
      return this == o || o instanceof VariableInfo && variable.equals(((VariableInfo)o).variable);
    }

    @Override
    public int hashCode() {
      return variable.hashCode();
    }
  }

  /**
   * A kind of final variable problem returned from {@link #getFinalVariableProblemsInBlock(Map, PsiElement)}
   * which designates a final variable which is initialized in a loop.
   */
  private static class InitializedInLoopProblemInfo extends VariableInfo {
    InitializedInLoopProblemInfo(@NotNull PsiVariable variable, @Nullable PsiElement expression) {
      super(variable, expression);
    }
  }

  private static void merge(int offset, CopyOnWriteSet source, CopyOnWriteSet @NotNull [] target) {
    if (source != null) {
      CopyOnWriteSet existing = target[offset];
      target[offset] = existing == null ? source : existing.addAll(source);
    }
  }

  /**
   * @return list of PsiReferenceExpression of usages of non-initialized local variables
   */
  public static @NotNull List<PsiReferenceExpression> getReadBeforeWriteLocals(@NotNull ControlFlow flow) {
    final InstructionClientVisitor<List<PsiReferenceExpression>> visitor = new ReadBeforeWriteClientVisitor(flow, true);
    depthFirstSearch(flow, visitor);
    return visitor.getResult();
  }

  public static @NotNull List<PsiReferenceExpression> getReadBeforeWrite(@NotNull ControlFlow flow) {
    if (flow.getSize() == 0) {
      return Collections.emptyList();
    }
    final ReadBeforeWriteClientVisitor visitor = new ReadBeforeWriteClientVisitor(flow, false);
    depthFirstSearch(flow, visitor);
    return visitor.getResult();
  }

  private static class ReadBeforeWriteClientVisitor extends InstructionClientVisitor<List<PsiReferenceExpression>> {
    // map of variable->PsiReferenceExpressions for all read before written variables for this point and below in control flow
    private final CopyOnWriteSet[] readVariables;
    private final ControlFlow myFlow;
    private final boolean localVariablesOnly;

    ReadBeforeWriteClientVisitor(@NotNull ControlFlow flow, boolean localVariablesOnly) {
      myFlow = flow;
      this.localVariablesOnly = localVariablesOnly;
      readVariables = new CopyOnWriteSet[myFlow.getSize() + 1];
    }

    @Override
    public void visitReadVariableInstruction(ReadVariableInstruction instruction, int offset, int nextOffset) {
      CopyOnWriteSet readVars = readVariables[Math.min(nextOffset, myFlow.getSize())];
      final PsiVariable variable = instruction.variable;
      if (!localVariablesOnly || !isImplicitlyInitialized(variable)) {
        final PsiReferenceExpression expression = getEnclosingReferenceExpression(myFlow.getElement(offset), variable);
        if (expression != null) {
          readVars = CopyOnWriteSet.add(readVars, new VariableInfo(variable, expression));
        }
      }
      merge(offset, readVars, readVariables);
    }

    @Override
    public void visitWriteVariableInstruction(WriteVariableInstruction instruction, int offset, int nextOffset) {
      CopyOnWriteSet readVars = readVariables[Math.min(nextOffset, myFlow.getSize())];
      if (readVars == null) return;

      final PsiVariable variable = instruction.variable;
      if (!localVariablesOnly || !isImplicitlyInitialized(variable)) {
        readVars = readVars.remove(new VariableInfo(variable, null));
      }
      merge(offset, readVars, readVariables);
    }

    private static boolean isImplicitlyInitialized(@NotNull PsiVariable variable) {
      if (variable instanceof PsiPatternVariable) return true;
      if (variable instanceof PsiParameter) {
        final PsiParameter parameter = (PsiParameter)variable;
        return !(parameter.getDeclarationScope() instanceof PsiForeachStatement);
      }
      return false;
    }

    @Override
    public void visitInstruction(Instruction instruction, int offset, int nextOffset) {
      merge(offset, readVariables[Math.min(nextOffset, myFlow.getSize())], readVariables);
    }

    @Override
    public void visitCallInstruction(CallInstruction instruction, int offset, int nextOffset) {
      visitInstruction(instruction, offset, nextOffset);
      for (int i = instruction.procBegin; i <= instruction.procEnd; i++) {
        readVariables[i] = null;
      }
    }

    @Override
    public @NotNull List<PsiReferenceExpression> getResult() {
      final CopyOnWriteSet topReadVariables = readVariables[0];
      if (topReadVariables == null) return Collections.emptyList();

      final List<PsiReferenceExpression> result = new ArrayList<>();
      for (VariableInfo info : topReadVariables.getSet()) {
        result.add((PsiReferenceExpression)info.expression);
      }
      Collections.sort(result, PsiUtil.BY_POSITION);
      return result;
    }
  }

  public static final int NORMAL_COMPLETION_REASON = 1;
  private static final int RETURN_COMPLETION_REASON = 2;

  /**
   * return reasons.normalCompletion when  block can complete normally
   * reasons.returnCalled when  block can complete abruptly because of return statement executed
   */
  public static int getCompletionReasons(@NotNull ControlFlow flow, int offset, int endOffset) {
    class MyVisitor extends InstructionClientVisitor<Integer> {
      private final boolean[] normalCompletion = new boolean[endOffset];
      private final boolean[] returnCalled = new boolean[endOffset];

      @Override
      public void visitInstruction(Instruction instruction, int offset, int nextOffset) {
        boolean ret = nextOffset < endOffset && returnCalled[nextOffset];
        boolean normal = nextOffset < endOffset && normalCompletion[nextOffset];
        final PsiElement element = flow.getElement(offset);
        boolean goToReturn = instruction instanceof GoToInstruction && ((GoToInstruction)instruction).isReturn;
        if (goToReturn || isInsideReturnStatement(element)) {
          ret = true;
        }
        else if (instruction instanceof ConditionalThrowToInstruction) {
          final int throwOffset = ((ConditionalThrowToInstruction)instruction).offset;
          boolean normalWhenThrow = throwOffset < endOffset && normalCompletion[throwOffset];
          boolean normalWhenNotThrow = offset == endOffset - 1 || normalCompletion[offset + 1];
          normal = normalWhenThrow || normalWhenNotThrow;
        }
        else if (!(instruction instanceof ThrowToInstruction) && nextOffset >= endOffset) {
          normal = true;
        }
        returnCalled[offset] |= ret;
        normalCompletion[offset] |= normal;
      }

      @Override
      public @NotNull Integer getResult() {
        return (returnCalled[offset] ? RETURN_COMPLETION_REASON : 0) | (normalCompletion[offset] ? NORMAL_COMPLETION_REASON : 0);
      }
    }
    MyVisitor visitor = new MyVisitor();
    depthFirstSearch(flow, visitor, offset, endOffset);

    return visitor.getResult();
  }

  public static @NotNull Collection<VariableInfo> getInitializedTwice(@NotNull ControlFlow flow) {
    return getInitializedTwice(flow, 0, flow.getSize());
  }

  public static @NotNull Collection<VariableInfo> getInitializedTwice(@NotNull ControlFlow flow, int startOffset, int endOffset) {
    Collection<VariableInfo> result = new HashSet<>();
    while (startOffset < endOffset) {
      InitializedTwiceClientVisitor visitor = new InitializedTwiceClientVisitor(flow, startOffset);
      depthFirstSearch(flow, visitor, startOffset, endOffset);
      result.addAll(visitor.getResult());
      startOffset = findUnprocessed(startOffset, endOffset, visitor);
    }
    return result;
  }

  private static class InitializedTwiceClientVisitor extends InstructionClientVisitor<Collection<VariableInfo>> {
    // map of variable->PsiReferenceExpressions for all read and not written variables for this point and below in control flow
    private final CopyOnWriteSet[] writtenVariables;
    private final CopyOnWriteSet[] writtenTwiceVariables;
    private final ControlFlow myFlow;
    private final int myStartOffset;

    InitializedTwiceClientVisitor(@NotNull ControlFlow flow, int startOffset) {
      myFlow = flow;
      myStartOffset = startOffset;
      writtenVariables = new CopyOnWriteSet[myFlow.getSize() + 1];
      writtenTwiceVariables = new CopyOnWriteSet[myFlow.getSize() + 1];
    }

    @Override
    public void visitInstruction(Instruction instruction, int offset, int nextOffset) {
      final int safeNextOffset = Math.min(nextOffset, myFlow.getSize());

      CopyOnWriteSet writeVars = writtenVariables[safeNextOffset];
      CopyOnWriteSet writeTwiceVars = writtenTwiceVariables[safeNextOffset];
      if (instruction instanceof WriteVariableInstruction) {
        final PsiVariable variable = ((WriteVariableInstruction)instruction).variable;

        final PsiElement latestWriteVarExpression = getLatestWriteVarExpression(writeVars, variable);

        if (latestWriteVarExpression == null) {
          final PsiElement expression = getExpression(myFlow.getElement(offset));
          writeVars = CopyOnWriteSet.add(writeVars, new VariableInfo(variable, expression));
        }
        else {
          writeTwiceVars = CopyOnWriteSet.add(writeTwiceVars, new VariableInfo(variable, latestWriteVarExpression));
        }
      }
      merge(offset, writeVars, writtenVariables);
      merge(offset, writeTwiceVars, writtenTwiceVariables);
    }

    private static @Nullable PsiElement getExpression(@NotNull PsiElement element) {
      if (element instanceof PsiAssignmentExpression) {
        PsiExpression target = PsiUtil.skipParenthesizedExprDown(((PsiAssignmentExpression)element).getLExpression());
        return ObjectUtils.tryCast(target, PsiReferenceExpression.class);
      }
      if (element instanceof PsiUnaryExpression) {
        PsiExpression target = PsiUtil.skipParenthesizedExprDown(((PsiUnaryExpression)element).getOperand());
        return ObjectUtils.tryCast(target, PsiReferenceExpression.class);
      }
      if (element instanceof PsiDeclarationStatement) {
        //should not happen
        return element;
      }
      return null;
    }

    private static @Nullable PsiElement getLatestWriteVarExpression(@Nullable CopyOnWriteSet writeVars, @NotNull PsiVariable variable) {
      if (writeVars == null) return null;

      PsiManager psiManager = variable.getManager();
      for (VariableInfo variableInfo : writeVars.getSet()) {
        if (psiManager.areElementsEquivalent(variableInfo.variable, variable)) {
          return variableInfo.expression;
        }
      }
      return null;
    }

    @Override
    public @NotNull Collection<VariableInfo> getResult() {
      final CopyOnWriteSet writtenTwiceVariable = writtenTwiceVariables[myStartOffset];
      if (writtenTwiceVariable == null) return Collections.emptyList();
      return writtenTwiceVariable.getSet();
    }
  }

  /**
   * Find locations of writes of variables from writeVars that happened before one of reads of variables from readVars.
   *
   * @param stopPoint point until which reads are considered
   * @return locations of writes
   */
  public static @NotNull Map<PsiElement, PsiVariable> getWritesBeforeReads(@NotNull ControlFlow flow,
                                                                  @NotNull Set<? extends PsiVariable> writeVars,
                                                                  @NotNull Set<? extends PsiVariable> readVars,
                                                                  int stopPoint) {
    Map<PsiElement, PsiVariable> writes = new LinkedHashMap<>();
    List<Instruction> instructions = flow.getInstructions();

    for (int i = 0; i < instructions.size(); i++) {
      Instruction instruction = instructions.get(i);
      if (!(instruction instanceof WriteVariableInstruction)) continue;

      PsiVariable writtenVar = ((WriteVariableInstruction)instruction).variable;
      if (!writeVars.contains(writtenVar)) continue;

      if (readBeforeStopPoint(flow, readVars, i, stopPoint)) writes.put(flow.getElement(i), writtenVar);
    }

    return writes;
  }

  /**
   * Check if any of given variables was read after start point and before stop point or before next write to this variable.
   *
   * @return true if it was read
   */
  private static boolean readBeforeStopPoint(@NotNull ControlFlow flow,
                                             @NotNull Set<? extends PsiVariable> readVars,
                                             int startOffset,
                                             int stopPoint) {
    class MyVisitor extends InstructionClientVisitor<Boolean> {

      private boolean reachable = false;

      @Override
      public void visitInstruction(Instruction instruction, int offset, int nextOffset) {

        if (offset == stopPoint || isWriteToReadVar(instruction)) {
          // since it's dfs if we even already found some reads, they happened after stop point or after reassignment
          reachable = false;
          return;
        }

        boolean foundRead = instruction instanceof ReadVariableInstruction &&
                            readVars.contains(((ReadVariableInstruction)instruction).variable);

        reachable |= foundRead;
      }

      private boolean isWriteToReadVar(Instruction instruction) {
        return instruction instanceof WriteVariableInstruction &&
               readVars.contains(((WriteVariableInstruction)instruction).variable);
      }

      @Override
      public Boolean getResult() {
        return reachable;
      }
    }

    MyVisitor visitor = new MyVisitor();
    depthFirstSearch(flow, visitor, startOffset, flow.getSize());

    return visitor.getResult();
  }

  /**
   * @return true if instruction at 'instructionOffset' is reachable from offset 'startOffset'
   */
  public static boolean isInstructionReachable(@NotNull ControlFlow flow, int instructionOffset, int startOffset) {
    return areInstructionsReachable(flow, new int[]{instructionOffset}, startOffset);
  }

  private static boolean areInstructionsReachable(@NotNull ControlFlow flow, int @NotNull [] instructionOffsets, int startOffset) {
    class MyVisitor extends InstructionClientVisitor<Boolean> {
      private boolean reachable;

      @Override
      public void visitInstruction(Instruction instruction, int offset, int nextOffset) {
        reachable |= ArrayUtil.indexOf(instructionOffsets, nextOffset) >= 0;
      }

      @Override
      public @NotNull Boolean getResult() {
        return reachable;
      }
    }

    if (startOffset != 0 && hasCalls(flow)) {
      // Additional computations are required to take into account CALL and RETURN instructions in the case where
      // the start offset isn't the beginning of the control flow, because we couldn't know the correct state
      // of the call stack if we started traversal of the control flow from an offset in the middle.
      return areInstructionsReachableWithCalls(flow, instructionOffsets, startOffset);
    }
    MyVisitor visitor = new MyVisitor();
    depthFirstSearch(flow, visitor, startOffset, flow.getSize());

    return visitor.getResult();
  }

  private static boolean hasCalls(@NotNull ControlFlow flow) {
    for (Instruction instruction : flow.getInstructions()) {
      if (instruction instanceof CallInstruction) {
        return true;
      }
    }
    return false;
  }

  private abstract static class ControlFlowGraph extends InstructionClientVisitor<Void> {
    // The graph is sparse: simple instructions have 1 next offset, branching - 2 next offsets, RETURN may have many (one per call)
    final int[][] nextOffsets;

    ControlFlowGraph(int size) {
      nextOffsets = new int[size][];
    }

    @Override
    public void visitInstruction(Instruction instruction, int offset, int nextOffset) {
      if (nextOffset > size()) nextOffset = size();
      addArc(offset, nextOffset);
    }

    void addArc(int offset, int nextOffset) {
      if (nextOffsets[offset] == null) {
        nextOffsets[offset] = new int[]{nextOffset, -1};
      }
      else {
        int[] targets = nextOffsets[offset];
        if (ArrayUtil.indexOf(targets, nextOffset) < 0) {
          int freeIndex = ArrayUtil.indexOf(targets, -1);
          if (freeIndex >= 0) {
            targets[freeIndex] = nextOffset;
          }
          else {
            int oldLength = targets.length;
            nextOffsets[offset] = targets = ArrayUtil.realloc(targets, oldLength * 3 / 2);
            Arrays.fill(targets, oldLength, targets.length, -1);
            targets[oldLength] = nextOffset;
          }
        }
      }
    }

    int @NotNull [] getNextOffsets(int offset) {
      return nextOffsets[offset] != null ? nextOffsets[offset] : ArrayUtilRt.EMPTY_INT_ARRAY;
    }

    int size() {
      return nextOffsets.length;
    }

    @Override
    public String toString() {
      StringBuilder s = new StringBuilder();
      for (int i = 0; i < nextOffsets.length; i++) {
        int[] targets = nextOffsets[i];
        if (targets != null && targets.length != 0 && targets[0] != -1) {
          if (s.length() != 0) s.append(' ');
          s.append('(').append(i).append("->");
          for (int j = 0; j < targets.length && targets[j] != -1; j++) {
            if (j != 0) s.append(",");
            s.append(targets[j]);
          }
          s.append(')');
        }
      }
      return s.toString();
    }

    boolean depthFirstSearch(int startOffset) {
      return depthFirstSearch(startOffset, new BitSet(size()));
    }

    boolean depthFirstSearch(int startOffset, @NotNull BitSet visitedOffsets) {
      // traverse the graph starting with the startOffset
      IntStack walkThroughStack = new IntArrayList(Math.max(size() / 2, 2));
      visitedOffsets.clear();
      walkThroughStack.push(startOffset);
      while (!walkThroughStack.isEmpty()) {
        int currentOffset = walkThroughStack.popInt();
        if (currentOffset < size() && !visitedOffsets.get(currentOffset)) {
          visitedOffsets.set(currentOffset);
          int[] nextOffsets = getNextOffsets(currentOffset);
          for (int nextOffset : nextOffsets) {
            if (nextOffset == -1) break;
            if (isComplete(currentOffset, nextOffset)) {
              return true;
            }
            walkThroughStack.push(nextOffset);
          }
        }
      }
      return false;
    }

    @Override
    public Void getResult() {
      return null;
    }

    boolean isComplete(int offset, int nextOffset) {
      return false;
    }

    void buildFrom(@NotNull ControlFlow flow) {
      // traverse the whole flow to collect the graph edges
      ControlFlowUtil.depthFirstSearch(flow, this, 0, flow.getSize());
    }
  }

  private static boolean areInstructionsReachableWithCalls(@NotNull ControlFlow flow,
                                                           int @NotNull [] instructionOffsets,
                                                           int startOffset) {
    ControlFlowGraph graph = new ControlFlowGraph(flow.getSize()) {
      @Override
      boolean isComplete(int offset, int nextOffset) {
        return ArrayUtil.indexOf(instructionOffsets, nextOffset) >= 0;
      }
    };
    graph.buildFrom(flow);
    return graph.depthFirstSearch(startOffset);
  }

  public static boolean isVariableAssignedInLoop(@NotNull PsiReferenceExpression expression, @NotNull PsiElement resolved) {
    if (!(expression.getParent() instanceof PsiAssignmentExpression)
        || ((PsiAssignmentExpression)expression.getParent()).getLExpression() != expression) {
      return false;
    }
    PsiExpression qualifier = expression.getQualifierExpression();
    if (qualifier != null && !(qualifier instanceof PsiThisExpression)) return false;

    if (!(resolved instanceof PsiVariable)) return false;
    PsiVariable variable = (PsiVariable)resolved;

    final PsiElement codeBlock = PsiUtil.getVariableCodeBlock(variable, expression);
    if (codeBlock == null) return false;
    final ControlFlow flow;
    try {
      ControlFlowFactory factory = ControlFlowFactory.getInstance(codeBlock.getProject());
      flow = factory.getControlFlow(codeBlock, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance(), true);
    }
    catch (AnalysisCanceledException e) {
      return false;
    }
    final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)expression.getParent();
    int startOffset = flow.getStartOffset(assignmentExpression);
    return startOffset != -1 && isInstructionReachable(flow, startOffset, startOffset);
  }

  static boolean isCaughtExceptionType(@NotNull PsiClassType throwType, @NotNull PsiType catchType) {
    return catchType.isAssignableFrom(throwType) || mightBeAssignableFromSubclass(throwType, catchType);
  }

  private static boolean mightBeAssignableFromSubclass(@NotNull PsiClassType throwType, @NotNull PsiType catchType) {
    if (catchType instanceof PsiDisjunctionType) {
      for (PsiType catchDisjunction : ((PsiDisjunctionType)catchType).getDisjunctions()) {
        if (throwType.isAssignableFrom(catchDisjunction)) {
          return true;
        }
      }
      return false;
    }
    return throwType.isAssignableFrom(catchType);
  }

  /**
   * Check that in the <code>flow</code> between <code>startOffset</code> and <code>endOffset</code> there are no writes
   * to the <code>variables</code> or these writes aren't observable at the <code>locations</code>.
   */
  public static boolean areVariablesUnmodifiedAtLocations(@NotNull ControlFlow flow,
                                                          int startOffset,
                                                          int endOffset,
                                                          @NotNull Set<? extends PsiVariable> variables,
                                                          @NotNull Iterable<? extends PsiElement> locations) {
    List<Instruction> instructions = flow.getInstructions();
    startOffset = Math.max(startOffset, 0);
    endOffset = Math.min(endOffset, instructions.size());

    IntList locationOffsetList = new IntArrayList();
    for (PsiElement location : locations) {
      int offset = flow.getStartOffset(location);
      if (offset >= startOffset && offset < endOffset) {
        locationOffsetList.add(offset);
      }
    }
    int[] locationOffsets = locationOffsetList.toIntArray();

    for (int offset = startOffset; offset < endOffset; offset++) {
      Instruction instruction = instructions.get(offset);
      if (instruction instanceof WriteVariableInstruction && variables.contains(((WriteVariableInstruction)instruction).variable)) {
        if (areInstructionsReachable(flow, locationOffsets, offset)) {
          return false;
        }
      }
    }
    return true;
  }
}