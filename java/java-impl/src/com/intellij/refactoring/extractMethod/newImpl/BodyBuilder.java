// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl;

import com.intellij.psi.*;
import com.intellij.refactoring.extractMethod.newImpl.structures.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.refactoring.extractMethod.newImpl.structures.CodeFragment.findSameElementsInCopy;
import static com.intellij.refactoring.extractMethod.newImpl.structures.CodeFragment.findSameGroupsInCopy;

public class BodyBuilder {
  private final PsiElementFactory factory;
  private final CodeFragment fragment;

  private List<List<PsiExpression>> inputGroups = Collections.emptyList();
  private List<String> inputSubstitutions = Collections.emptyList();
  private List<PsiVariable> missedDeclarations = Collections.emptyList();

  private List<PsiStatement> specialExits = Collections.emptyList();
  private String specialSubstitution = "";

  private @Nullable String defaultReturnExpression = null;

  public BodyBuilder(CodeFragment fragment) {
    this.fragment = fragment;
    this.factory = PsiElementFactory.getInstance(fragment.getProject());
  }

  public BodyBuilder inputGroups(List<List<PsiExpression>> inputGroups) {
    this.inputGroups = inputGroups;
    return this;
  }

  public BodyBuilder inputSubstitutions(List<String> substitutions) {
    this.inputSubstitutions = substitutions;
    return this;
  }

  public BodyBuilder specialExits(List<PsiStatement> exits, String substitution) {
    this.specialExits = exits;
    this.specialSubstitution = substitution;
    return this;
  }

  public BodyBuilder defaultReturn(String defaultReturnExpression) {
    this.defaultReturnExpression = defaultReturnExpression;
    return this;
  }

  public BodyBuilder missedDeclarations(List<PsiVariable> variables) {
    this.missedDeclarations = variables;
    return this;
  }

  public List<List<PsiExpression>> inputGroups(){
    return inputGroups;
  }

  public PsiCodeBlock build() {
    CodeFragment fragmentCopy = CodeFragment.copyAsCodeBlockOf(fragment);
    final BodyBuilder onCopyBuilder = new BodyBuilder(fragmentCopy)
      .inputGroups(findSameGroupsInCopy(fragment, fragmentCopy, inputGroups))
      .inputSubstitutions(inputSubstitutions)
      .missedDeclarations(missedDeclarations)
      .specialExits(findSameElementsInCopy(fragment, fragmentCopy, specialExits), specialSubstitution)
      .defaultReturn(defaultReturnExpression);
    final List<PsiSubstitution> substitutions = onCopyBuilder.createSubstitutions();
    substitutions.forEach(substitution -> substitution.getAction().run());
    return (PsiCodeBlock)fragmentCopy.getCommonParent();
  }

  private List<PsiSubstitution> createSubstitutions() {
    return flatten(
      addDefaultReturn(fragment, defaultReturnExpression),
      addMissedDeclarations(fragment, missedDeclarations),
      replaceInputGroup(inputGroups, inputSubstitutions),
      replaceSpecialExits(specialExits, specialSubstitution)
    );
  }

  private List<PsiSubstitution> replaceInputGroup(List<List<PsiExpression>> inputGroups, List<String> names) {
    if (inputGroups.size() != names.size()) throw new IllegalArgumentException("Number of groups and names is different");
    final ArrayList<PsiSubstitution> substitutions = new ArrayList<>();
    for (int i = 0; i < inputGroups.size(); i++) {
      substitutions.addAll(replaceInputGroup(inputGroups.get(i), names.get(i)));
    }
    return substitutions;
  }

  private List<PsiSubstitution> replaceInputGroup(List<PsiExpression> inputGroup, String name) {
    return ContainerUtil.map(inputGroup, (reference) -> {
      final PsiExpression newReference = factory.createExpressionFromText(name, null);
      return PsiSubstitutionFactory.createReplace(reference, newReference);
    });
  }

  private List<PsiSubstitution> addMissedDeclarations(CodeFragment fragment, List<PsiVariable> missedVariables) {
    return missedVariables.stream()
      .map(variable -> factory.createVariableDeclarationStatement(Objects.requireNonNull(variable.getName()), variable.getType(), null))
      .map(declaration -> PsiSubstitutionFactory.createAddBefore(fragment.getFirstElement(), declaration))
      .collect(Collectors.toList());
  }

  private List<PsiSubstitution> replaceSpecialExits(List<PsiStatement> specialExits, String substitution) {
    if (specialExits == null || substitution == null) return Collections.emptyList();
    return ContainerUtil.map(specialExits, exit -> {
      final PsiStatement returnStatement = factory.createStatementFromText(substitution, null);
      return PsiSubstitutionFactory.createReplace(exit, returnStatement);
    });
  }

  private List<PsiSubstitution> addDefaultReturn(CodeFragment fragment, @Nullable String returnExpression) {
    if (returnExpression == null) return Collections.emptyList();
    final PsiStatement returnStatement = factory.createStatementFromText("return " + returnExpression + ";", null);
    final PsiSubstitution substitution = PsiSubstitutionFactory.createAddAfter(fragment.getLastElement(), returnStatement);
    return Collections.singletonList(substitution);
  }

  private static List<PsiSubstitution> flatten(List<PsiSubstitution>... substitutions) {
    return ContainerUtil.flatten(substitutions);
  }
}