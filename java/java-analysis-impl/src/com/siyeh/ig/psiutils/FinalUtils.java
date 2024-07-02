// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.psiutils;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.psi.controlFlow.LocalsOrMyInstanceFieldsControlFlowPolicy;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil.variableDefinitelyAssignedIn;

public final class FinalUtils {
  private FinalUtils() {}

  public static boolean canBeFinal(@NotNull PsiVariable variable) {
    if (variable.getInitializer() != null || variable instanceof PsiParameter) {
      // parameters have an implicit initializer
      return !VariableAccessUtils.variableIsAssigned(variable);
    }
    if (variable instanceof PsiField && !HighlightControlFlowUtil.isFieldInitializedAfterObjectConstruction((PsiField)variable)) {
      return false;
    }
    return checkIfElementViolatesFinality(variable);
  }

  /**
   * This method is to be used in the Java 2 Kotlin conversion to determine variables that should be defined
   * with "val" (implying immutability)
   * Immutable means it is defined only once and so thus should be defined in the format "val foo = bar"
   * <p>
   * @param variable the variable to check for immutability
   * @return true if variable is kotlin immutable
   */
  public static boolean canBeKotlinImmutable(@NotNull PsiVariable variable) {
    if (variable.getInitializer() != null || variable instanceof PsiParameter) {
      // parameters have an implicit initializer
      return !VariableAccessUtils.variableIsAssigned(variable);
    }
    if (variable instanceof PsiField && fieldConstructionImpliesMutable((PsiField)variable)) {
      return false;
    }
    return checkIfElementViolatesImmutability(variable);
  }

  private static boolean fieldConstructionImpliesMutable(@NotNull PsiField field) {
    PsiClass aClass = field.getContainingClass();
    if (aClass == null) return false;
    // instance field should be initialized at the end of each constructor
    PsiMethod[] constructors = aClass.getConstructors();
    List<PsiMethod> usefulConstructors = new ArrayList<>();

    for (PsiMethod constructor : constructors) {
      PsiCodeBlock ctrBody = constructor.getBody();
      if (ctrBody == null) return true;
      List<PsiMethod> redirectedConstructors = JavaHighlightUtil.getChainedConstructors(constructor);
      List<PsiMethod> usefulRedirectedConstructors = new ArrayList<>();
      for (PsiMethod redirectedConstructor : redirectedConstructors) {
        PsiCodeBlock body = redirectedConstructor.getBody();
        if (body != null && (variableDefinitelyAssignedIn(field, body) || isValidThisMethodInConstructor(redirectedConstructor))) {
          usefulRedirectedConstructors.add(redirectedConstructor);
        }
      }
      if (!usefulRedirectedConstructors.isEmpty() && usefulRedirectedConstructors.size() != redirectedConstructors.size()) return true;
      if (ctrBody.isValid() && (variableDefinitelyAssignedIn(field, ctrBody) || isValidThisMethodInConstructor(constructor))) {
        usefulConstructors.add(constructor);
      }
    }
    return (!usefulConstructors.isEmpty() && usefulConstructors.size() != constructors.length);
  }

  private static boolean isValidThisMethodInConstructor(@NotNull PsiMethod constructor) {
    ArrayList<PsiElement> children = new ArrayList<>(
      Arrays.stream(constructor.getChildren()).filter(child -> child instanceof PsiCodeBlock).flatMap(block -> Arrays.stream(
        block.getChildren())).toList());

    while (!children.isEmpty()) {
      PsiElement child = children.remove(0);
      if (!(child instanceof PsiMethodCallExpression)) {
        children.addAll(Arrays.asList(child.getChildren()));
        continue;
      }
      if (((PsiMethodCallExpression)child).getMethodExpression().getQualifiedName().equals("this")) {
        return true;
      }
    }
    return false;
  }

  private static boolean checkIfElementViolatesFinality(@NotNull PsiVariable variable) {
    PsiElement scope = variable instanceof PsiField
                       ? PsiUtil.getTopLevelClass(variable)
                       : PsiUtil.getVariableCodeBlock(variable, null);
    if (scope == null) return false;
    Map<PsiElement, Collection<ControlFlowUtil.VariableInfo>> finalVarProblems = new HashMap<>();
    Map<PsiElement, Collection<PsiReferenceExpression>> uninitializedVarProblems = new HashMap<>();
    PsiElementProcessor<PsiElement> elementDoesNotViolateFinality = e -> {
      return checkElementDoesNotViolateFinality(e, variable, uninitializedVarProblems, finalVarProblems);
    };
    return PsiTreeUtil.processElements(scope, elementDoesNotViolateFinality);
  }

  private static boolean checkIfElementViolatesImmutability(@NotNull PsiVariable variable) {
    PsiElement scope = variable instanceof PsiField
                       ? PsiUtil.getTopLevelClass(variable)
                       : PsiUtil.getVariableCodeBlock(variable, null);
    if (scope == null) return false;
    Map<PsiElement, Collection<ControlFlowUtil.VariableInfo>> finalVarProblems = new HashMap<>();
    Map<PsiElement, Collection<PsiReferenceExpression>> uninitializedVarProblems = new HashMap<>();
    PsiElementProcessor<PsiElement> elementDoesNotViolateFinality = e -> {
      return checkElementDoesNotViolateImmutability(e, variable, uninitializedVarProblems, finalVarProblems);
    };
    return PsiTreeUtil.processElements(scope, elementDoesNotViolateFinality);
  }

  private static boolean checkElementDoesNotViolateImmutability(PsiElement e,
                                                                PsiVariable variable,
                                                                Map<PsiElement, Collection<PsiReferenceExpression>> uninitializedVarProblems,
                                                                Map<PsiElement, Collection<ControlFlowUtil.VariableInfo>> finalVarProblems) {

    if (!checkElementDoesNotViolateFinality(e, variable, uninitializedVarProblems, finalVarProblems)) return false;
    if (!(e instanceof PsiReferenceExpression ref)) return true;
    if (!ref.isReferenceTo(variable)) return true;
    if (!(variable instanceof PsiField)) return true;
    PsiMember enclosingInitializer = PsiUtil.findEnclosingConstructorOrInitializer(ref);
    if (enclosingInitializer == null) return true;
    PsiElement parent = ref;
    Collection<PsiIfStatement> conditionals = new ArrayList<>();
    while (parent != enclosingInitializer) {
      parent = parent.getParent();
      if (parent instanceof PsiIfStatement) {
        conditionals.add((PsiIfStatement)parent);
        break;
      }
    }
    return conditionals.isEmpty();
  }

  private static boolean checkElementDoesNotViolateFinality(PsiElement e,
                                                            PsiVariable variable,
                                                            Map<PsiElement, Collection<PsiReferenceExpression>> uninitializedVarProblems,
                                                            Map<PsiElement, Collection<ControlFlowUtil.VariableInfo>> finalVarProblems) {
    if (!(e instanceof PsiReferenceExpression ref)) return true;
    if (!ref.isReferenceTo(variable)) return true;
    HighlightInfo.Builder highlightInfo = HighlightControlFlowUtil
      .checkVariableInitializedBeforeUsage(ref, variable, uninitializedVarProblems, variable.getContainingFile(), true);
    if (highlightInfo != null) return false;
    if (!PsiUtil.isAccessedForWriting(ref)) return true;
    if (!LocalsOrMyInstanceFieldsControlFlowPolicy.isLocalOrMyInstanceReference(ref)) return false;
    if (ControlFlowUtil.isVariableAssignedInLoop(ref, variable)) return false;
    if (variable instanceof PsiField) {
      if (PsiUtil.findEnclosingConstructorOrInitializer(ref) == null) return false;
      PsiElement innerScope = HighlightControlFlowUtil.getElementVariableReferencedFrom(variable, ref);
      if (innerScope != null && innerScope != ((PsiField)variable).getContainingClass()) return false;
    }
    HighlightInfo.Builder random =
      HighlightControlFlowUtil.checkFinalVariableMightAlreadyHaveBeenAssignedTo(variable, ref, finalVarProblems);
    return random == null;
  }
}