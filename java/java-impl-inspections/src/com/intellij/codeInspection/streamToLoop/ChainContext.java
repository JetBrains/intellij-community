// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.streamToLoop;

import com.intellij.lang.java.lexer.JavaLexer;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Provides context that can be used during transformation of chained method calls (e.g. stream calls chain, optional calls chain).
 * <p>
 * Allows to:
 * 1. register newly allocated variables' names
 * 2. add before / after steps that can be added after transformation is complete
 * 3. create different elements using PsiElementFactory
 */
public class ChainContext {

  private final PsiElementFactory myFactory;
  private final List<String> myBeforeSteps = new ArrayList<>();
  private final List<String> myAfterSteps = new ArrayList<>();
  public PsiElement myChainExpression;
  final Set<String> myUsedNames;

  public ChainContext(@NotNull PsiElement chainExpression) {
    myChainExpression = chainExpression;
    myFactory = JavaPsiFacade.getElementFactory(chainExpression.getProject());
    myUsedNames = new HashSet<>();
  }

  /**
   * Constructs chain context using parent's context chain call, factory and registered names.
   */
  public ChainContext(@NotNull ChainContext parentContext) {
    myChainExpression = parentContext.myChainExpression;
    myFactory = parentContext.myFactory;
    myUsedNames = parentContext.myUsedNames;
  }

  /**
   * Choose name from variants for newly allocated variable and register it in context.
   * If all names from variants are already used then variable is named variant$i for i from 1 to Integer.MAX_INTEGER.
   * If no variants are present then variable is named either val or val$i.
   *
   * @param variants possible variable names
   * @return chosen name
   */
  public String registerVarName(@NotNull Collection<String> variants) {
    if (variants.isEmpty()) {
      return registerVarName(Collections.singleton("val"));
    }
    for (int idx = 0; ; idx++) {
      for (String variant : variants) {
        String name = idx == 0 ? variant : variant + idx;
        if (!isUsed(name)) {
          myUsedNames.add(name);
          return name;
        }
      }
    }
  }

  /**
   * Add variable declaration to beforeSteps.
   *
   * @param desiredName desired name that is registered in context
   * @param type        variable type
   * @param initializer variable initializer
   */
  public String declare(String desiredName, String type, String initializer) {
    String name = registerVarName(Collections.singleton(desiredName));
    myBeforeSteps.add(type + " " + name + " = " + initializer + ";");
    return name;
  }

  /**
   * Add step that will be added before transformation result.
   *
   * @param beforeStatement statement to add
   */
  public void addBeforeStep(String beforeStatement) {
    myBeforeSteps.add(beforeStatement);
  }

  /**
   * Get all before steps and remove them from context.
   *
   * @return before steps
   */
  public String drainBeforeSteps() {
    String beforeSteps = String.join("", myBeforeSteps);
    myBeforeSteps.clear();
    return beforeSteps;
  }

  /**
   * Add step that will be added after transformation result.
   *
   * @param afterStatement statement to add
   */
  public void addAfterStep(String afterStatement) {
    myAfterSteps.add(0, afterStatement);
  }

  /**
   * Get all after steps and remove them from context.
   *
   * @return before steps
   */
  public String drainAfterSteps() {
    String afterSteps = String.join("", myAfterSteps);
    myAfterSteps.clear();
    return afterSteps;
  }

  /**
   * Create expression from text in context of chain call.
   */
  public PsiExpression createExpression(String text) {
    return myFactory.createExpressionFromText(text, myChainExpression);
  }

  /**
   * Create statement from text in context of chain call.
   */
  public PsiStatement createStatement(String text) {
    return myFactory.createStatementFromText(text, myChainExpression);
  }

  /**
   * Create type from text in context of chain call.
   */
  public PsiType createType(String text) {
    return myFactory.createTypeFromText(text, myChainExpression);
  }

  private boolean isUsed(String varName) {
    return myUsedNames.contains(varName) || JavaLexer.isKeyword(varName, LanguageLevel.HIGHEST) ||
           !varName.equals(JavaCodeStyleManager.getInstance(getProject())
                             .suggestUniqueVariableName(varName, myChainExpression,
                                                        v -> PsiTreeUtil.isAncestor(myChainExpression, v, true)));
  }

  /**
   * Get chain call project.
   */
  public Project getProject() {
    return myChainExpression.getProject();
  }
}
