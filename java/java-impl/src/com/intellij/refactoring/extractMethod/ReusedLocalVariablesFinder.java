// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod;

import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.UniqueNameGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Finds local variables declared inside a code fragment and then used outside of that code fragment
 *
 * @author Pavel.Dolgov
 */
public class ReusedLocalVariablesFinder {
  private final ControlFlow myControlFlow;
  private final PsiStatement myNextStatement;
  private final int myOffset;
  private final JavaCodeStyleManager myCodeStyleManager;

  private ReusedLocalVariablesFinder(@NotNull ControlFlow controlFlow, @NotNull PsiStatement nextStatement, int offset) {
    myControlFlow = controlFlow;
    myNextStatement = nextStatement;
    myOffset = offset;
    myCodeStyleManager = JavaCodeStyleManager.getInstance(myNextStatement.getProject());
  }

  public static List<ReusedLocalVariable> findReusedLocalVariables(@NotNull PsiElement fragmentStart,
                                                                   @NotNull PsiElement fragmentEnd,
                                                                   @NotNull Set<PsiLocalVariable> ignoreVariables,
                                                                   @NotNull InputVariables inputVariables) {
    List<PsiLocalVariable> declaredVariables = getDeclaredVariables(fragmentStart, fragmentEnd, ignoreVariables);
    if (declaredVariables.isEmpty()) {
      return Collections.emptyList();
    }

    ReusedLocalVariablesFinder finder = createFinder(fragmentEnd);
    if (finder == null) {
      return Collections.emptyList();
    }

    List<PsiLocalVariable> reusedVariables = ContainerUtil.filter(declaredVariables, finder::isVariableReused);
    if (reusedVariables.isEmpty()) {
      return Collections.emptyList();
    }

    List<ReusedLocalVariable> result = new ArrayList<>();
    Set<String> tempNames = new HashSet<>(ContainerUtil.map(inputVariables.getInputVariables(), data -> data.name));
    for (PsiLocalVariable variable : reusedVariables) {
      String name = variable.getName();
      if (name == null) {
        continue;
      }
      String typeText = variable.getType().getCanonicalText();
      if (finder.isValueReused(variable)) {
        String suggestedName = finder.suggestUniqueVariableName(name);
        String tempName = UniqueNameGenerator.generateUniqueName(suggestedName, tempNames);
        tempNames.add(tempName);
        result.add(new ReusedLocalVariable(name, tempName, typeText, true));
      }
      else {
        result.add(new ReusedLocalVariable(name, null, typeText, false));
      }
    }
    return result;
  }

  private static List<PsiLocalVariable> getDeclaredVariables(@NotNull PsiElement start,
                                                             @NotNull PsiElement end,
                                                             @NotNull Set<PsiLocalVariable> ignoreVariables) {
    // Only the variables declared at the current code block's level can be reused after the end of the fragment.
    List<PsiLocalVariable> result = new SmartList<>();
    for (PsiElement element = start; element != null; element = element != end ? element.getNextSibling() : null) {
      if (element instanceof PsiDeclarationStatement) {
        PsiElement[] declaredElements = ((PsiDeclarationStatement)element).getDeclaredElements();
        for (PsiElement declaredElement : declaredElements) {
          if (declaredElement instanceof PsiLocalVariable && !ignoreVariables.contains(declaredElement)) {
            result.add((PsiLocalVariable)declaredElement);
          }
        }
      }
    }
    return result;
  }

  @Nullable
  private static ReusedLocalVariablesFinder createFinder(@NotNull PsiElement fragmentEnd) {
    PsiStatement nextStatement = PsiTreeUtil.getNextSiblingOfType(fragmentEnd, PsiStatement.class);
    if (nextStatement == null) {
      return null;
    }

    PsiElement codeFragment = ControlFlowUtil.findCodeFragment(nextStatement);
    ControlFlow controlFlow;
    try {
      controlFlow = ControlFlowFactory.getInstance(codeFragment.getProject()).getControlFlow(
        codeFragment, new LocalsControlFlowPolicy(codeFragment), false, false);
    }
    catch (AnalysisCanceledException e) {
      return null;
    }
    int offset = controlFlow.getStartOffset(nextStatement);
    if (offset < 0) {
      return null;
    }
    return new ReusedLocalVariablesFinder(controlFlow, nextStatement, offset);
  }

  private boolean isVariableReused(@NotNull PsiVariable variable) {
    return ControlFlowUtil.isVariableUsed(myControlFlow, myOffset, myControlFlow.getSize(), variable);
  }

  private boolean isValueReused(@NotNull PsiVariable variable) {
    return ControlFlowUtil.needVariableValueAt(variable, myControlFlow, myOffset);
  }

  private String suggestUniqueVariableName(String name) {
    return myCodeStyleManager.suggestUniqueVariableName(name, myNextStatement, true);
  }
}
