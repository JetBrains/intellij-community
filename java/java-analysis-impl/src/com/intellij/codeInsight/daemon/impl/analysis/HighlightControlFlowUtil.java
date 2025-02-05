// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.JavaPsiConstructorUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class HighlightControlFlowUtil {

  private static QuickFixFactory getQuickFixFactory() {
    return QuickFixFactory.getInstance();
  }

  private HighlightControlFlowUtil() { }

  /**
   * @deprecated use {@link ControlFlowFactory#getControlFlowNoConstantEvaluate(PsiElement)}
   */
  @Deprecated
  public static @NotNull ControlFlow getControlFlowNoConstantEvaluate(@NotNull PsiElement body) throws AnalysisCanceledException {
    return ControlFlowFactory.getControlFlowNoConstantEvaluate(body);
  }

  /**
   * @deprecated use {@link ControlFlowUtil#variableDefinitelyAssignedIn(PsiVariable, PsiElement)}
   */
  @Deprecated
  public static boolean variableDefinitelyAssignedIn(@NotNull PsiVariable variable, @NotNull PsiElement context) {
    return ControlFlowUtil.variableDefinitelyAssignedIn(variable, context);
  }

  private static @NotNull ControlFlow getControlFlow(@NotNull PsiElement context) throws AnalysisCanceledException {
    LocalsOrMyInstanceFieldsControlFlowPolicy policy = LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance();
    return ControlFlowFactory.getControlFlow(context, policy, ControlFlowOptions.create(true, true, true));
  }

  public static boolean isAssigned(@NotNull PsiParameter parameter) {
    ParamWriteProcessor processor = new ParamWriteProcessor();
    ReferencesSearch.search(parameter, new LocalSearchScope(parameter.getDeclarationScope()), true).forEach(processor);
    return processor.isWriteRefFound();
  }

  private static class ParamWriteProcessor implements Processor<PsiReference> {
    private volatile boolean myIsWriteRefFound;
    @Override
    public boolean process(@NotNull PsiReference reference) {
      PsiElement element = reference.getElement();
      if (element instanceof PsiReferenceExpression ref && PsiUtil.isAccessedForWriting(ref)) {
        myIsWriteRefFound = true;
        return false;
      }
      return true;
    }

    private boolean isWriteRefFound() {
      return myIsWriteRefFound;
    }
  }

  private static boolean variableDefinitelyNotAssignedIn(@NotNull PsiVariable variable, @NotNull PsiElement context) {
    try {
      return ControlFlowUtil.isVariableDefinitelyNotAssigned(variable, getControlFlow(context));
    }
    catch (AnalysisCanceledException e) {
      return true;
    }
  }

  /**
   * @param variable variable to check
   * @param finalVarProblems cache map to reuse information
   * @return true if variable is reassigned
   */
  public static boolean isReassigned(@NotNull PsiVariable variable,
                                     @NotNull Map<? super PsiElement, Collection<ControlFlowUtil.VariableInfo>> finalVarProblems) {
    if (variable instanceof PsiLocalVariable) {
      PsiElement parent = variable.getParent();
      if (parent == null) return false;
      PsiElement declarationScope = parent.getParent();
      if (declarationScope == null) return false;
      Collection<ControlFlowUtil.VariableInfo> codeBlockProblems = getFinalVariableProblemsInBlock(finalVarProblems, declarationScope);
      return codeBlockProblems.contains(new ControlFlowUtil.VariableInfo(variable, null));
    }
    if (variable instanceof PsiParameter parameter) {
      return isAssigned(parameter);
    }
    return false;
  }


  public static HighlightInfo.Builder checkFinalVariableMightAlreadyHaveBeenAssignedTo(@NotNull PsiVariable variable,
                                                                                       @NotNull PsiReferenceExpression expression,
                                                                                       @NotNull Map<? super PsiElement, Collection<ControlFlowUtil.VariableInfo>> finalVarProblems) {
    if (!PsiUtil.isAccessedForWriting(expression)) return null;

    PsiElement scope = variable instanceof PsiField
                       ? variable.getParent()
                       : variable.getParent() == null ? null : variable.getParent().getParent();
    PsiElement codeBlock = PsiUtil.getTopLevelEnclosingCodeBlock(expression, scope);
    if (codeBlock == null) return null;
    Collection<ControlFlowUtil.VariableInfo> codeBlockProblems = getFinalVariableProblemsInBlock(finalVarProblems, codeBlock);

    boolean inLoop = false;
    boolean canDefer = false;
    ControlFlowUtil.VariableInfo variableInfo = ContainerUtil.find(codeBlockProblems, vi -> vi.expression == expression);
    if (variableInfo != null) {
      inLoop = variableInfo instanceof InitializedInLoopProblemInfo;
      canDefer = !inLoop;
    }
    else if (!(variable instanceof PsiField field && isFieldInitializedInAnotherMember(field, expression, codeBlock))) {
      return null;
    }

    String description =
      JavaErrorBundle.message(inLoop ? "variable.assigned.in.loop" : "variable.already.assigned", variable.getName());
    HighlightInfo.Builder highlightInfo =
      HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(description);
    if (canDefer) {
      IntentionAction action = getQuickFixFactory().createDeferFinalAssignmentFix(variable, expression);
      highlightInfo.registerFix(action, null, null, null, null);
    }
    HighlightFixUtil.registerMakeNotFinalAction(variable, highlightInfo);
    return highlightInfo;
  }

  private static boolean isFieldInitializedInAnotherMember(@NotNull PsiField field,
                                                           @NotNull PsiReferenceExpression expression,
                                                           @NotNull PsiElement codeBlock) {
    PsiClass aClass = field.getContainingClass();
    if (aClass == null) return false;
    boolean isFieldStatic = field.hasModifierProperty(PsiModifier.STATIC);
    PsiMember enclosingConstructorOrInitializer = PsiUtil.findEnclosingConstructorOrInitializer(expression);

    if (!isFieldStatic) {
      // constructor that delegates to another constructor cannot assign final fields
      if (enclosingConstructorOrInitializer instanceof PsiMethod method) {
        PsiMethodCallExpression chainedCall = JavaPsiConstructorUtil.findThisOrSuperCallInConstructor(method);
        if (JavaPsiConstructorUtil.isChainedConstructorCall(chainedCall)) {
          return true;
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
      PsiElement context = member instanceof PsiField f ? f.getInitializer() : ((PsiClassInitializer)member).getBody();

      if (context != null
          && member.hasModifierProperty(PsiModifier.STATIC) == isFieldStatic
          && !variableDefinitelyNotAssignedIn(field, context)) {
        return context != codeBlock;
      }
    }
    return false;
  }

  private static @NotNull Collection<ControlFlowUtil.VariableInfo> getFinalVariableProblemsInBlock(@NotNull Map<? super PsiElement, Collection<ControlFlowUtil.VariableInfo>> finalVarProblems,
                                                                                                   @NotNull PsiElement codeBlock) {
    Collection<ControlFlowUtil.VariableInfo> codeBlockProblems = finalVarProblems.get(codeBlock);
    if (codeBlockProblems == null) {
      try {
        ControlFlow controlFlow = getControlFlow(codeBlock);
        codeBlockProblems = ControlFlowUtil.getInitializedTwice(controlFlow);
        codeBlockProblems = addReassignedInLoopProblems(codeBlockProblems, controlFlow);
      }
      catch (AnalysisCanceledException e) {
        codeBlockProblems = Collections.emptyList();
      }
      finalVarProblems.put(codeBlock, codeBlockProblems);
    }
    return codeBlockProblems;
  }

  private static Collection<ControlFlowUtil.VariableInfo> addReassignedInLoopProblems(
    @NotNull Collection<ControlFlowUtil.VariableInfo> codeBlockProblems,
    @NotNull ControlFlow controlFlow) {
    List<Instruction> instructions = controlFlow.getInstructions();
    for (int index = 0; index < instructions.size(); index++) {
      Instruction instruction = instructions.get(index);
      if (instruction instanceof WriteVariableInstruction wvi) {
        PsiVariable variable = wvi.variable;
        if (variable instanceof PsiLocalVariable || variable instanceof PsiField) {
          PsiElement anchor = controlFlow.getElement(index);
          if (anchor instanceof PsiAssignmentExpression assignment) {
            PsiExpression ref = PsiUtil.skipParenthesizedExprDown(assignment.getLExpression());
            if (ref instanceof PsiReferenceExpression) {
              ControlFlowUtil.VariableInfo varInfo = new InitializedInLoopProblemInfo(variable, ref);
              if (!codeBlockProblems.contains(varInfo) && ControlFlowUtil.isInstructionReachable(controlFlow, index, index)) {
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
   * A kind of final variable problem returned from {@link #getFinalVariableProblemsInBlock(Map, PsiElement)}
   * which designates a final variable which is initialized in a loop.
   */
  private static class InitializedInLoopProblemInfo extends ControlFlowUtil.VariableInfo {
    InitializedInLoopProblemInfo(@NotNull PsiVariable variable, @Nullable PsiElement expression) {
      super(variable, expression);
    }
  }
}
