// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes;

import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.CreateSwitchBranchesUtil;
import com.siyeh.ig.psiutils.SwitchUtils;
import com.siyeh.ig.psiutils.VariableNameGenerator;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;
import java.util.function.Function;

public final class CreateMissingDeconstructionRecordClassBranchesFix extends CreateMissingSwitchBranchesFix {

  private final List<String> allNames;
  private final List<String> missedShorten;

  private CreateMissingDeconstructionRecordClassBranchesFix(@NotNull PsiSwitchBlock block,
                                                            @NotNull Set<String> missedNames,
                                                            @NotNull List<String> allNames,
                                                            @NotNull List<String> missedShorten) {
    super(block, missedNames);
    this.allNames = allNames;
    this.missedShorten = missedShorten;
  }

  @Override
  protected String getText(@NotNull PsiSwitchBlock switchBlock) {
    return CreateSwitchBranchesUtil.getActionName(missedShorten.stream().sorted().toList());
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getFamilyName() {
    return InspectionGadgetsBundle.message("create.missing.record.deconstructions.switch.branches.fix.family.name");
  }

  @Override
  protected @NotNull List<String> getAllNames(@NotNull PsiClass aClass, @NotNull PsiSwitchBlock switchBlock) {
    return allNames;
  }

  @Override
  protected @NotNull Function<PsiSwitchLabelStatementBase, @Unmodifiable List<String>> getCaseExtractor() {
    return label -> {
      PsiCaseLabelElementList list = label.getCaseLabelElementList();
      if (list == null) return Collections.emptyList();
      return ContainerUtil.map(list.getElements(), PsiCaseLabelElement::getText);
    };
  }

  public static @Nullable CreateMissingDeconstructionRecordClassBranchesFix create(@NotNull PsiSwitchBlock switchBlock,
                                                                                   @NotNull PsiClass selectorType,
                                                                                   @NotNull Map<PsiType, Set<List<PsiType>>> missedBranches,
                                                                                   @NotNull List<? extends PsiCaseLabelElement> elements) {
    if (missedBranches.isEmpty()) {
      return null;
    }
    if (!selectorType.isRecord()) {
      return null;
    }
    if (missedBranches.values().stream().flatMap(t -> t.stream()).flatMap(t -> t.stream())
      .anyMatch(type -> {
        if (type == null) {
          return true;
        }
        PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(type);
        return (psiClass == null && !TypeConversionUtil.isPrimitiveAndNotNull(type)) ||
               (psiClass != null && psiClass.hasTypeParameters());
      })) {
      return null;
    }
    if (!SwitchUtils.isRuleFormatSwitch(switchBlock)) {
      return null;
    }
    List<String> allLabels = new ArrayList<>();
    int lastDeconstructionPatternIndex = -1;
    int i = -1;
    for (PsiCaseLabelElement element : elements) {
      i++;
      //put after last pattern, because missed cases cannot be dominated
      if (element instanceof PsiPattern) {
        lastDeconstructionPatternIndex = i;
      }
      if (element == null) return null;
      allLabels.add(element.getText());
    }
    List<String> missedLabels = getMissedLabels(switchBlock, missedBranches, false);
    if (missedLabels == null || missedLabels.isEmpty()) {
      return null;
    }
    allLabels.addAll(lastDeconstructionPatternIndex + 1, missedLabels);
    allLabels = allLabels.stream().distinct().toList();
    List<String> shortenLabels = getMissedLabels(switchBlock, missedBranches, true);
    if (shortenLabels == null) {
      return null;
    }
    return new CreateMissingDeconstructionRecordClassBranchesFix(switchBlock, new HashSet<>(missedLabels), allLabels, shortenLabels);
  }

  private static @Nullable List<String> getMissedLabels(@NotNull PsiSwitchBlock block,
                                                        @NotNull Map<PsiType, Set<List<PsiType>>> branchesByType,
                                                        boolean shorten)  {
    List<String> result = new ArrayList<>();
    JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(block.getProject());
    for (Map.Entry<PsiType, Set<List<PsiType>>> branches : branchesByType.entrySet()) {
      PsiType recordType = branches.getKey();
      if (recordType == null) {
        return null;
      }
      String recordTypeString = recordType.getCanonicalText();
      List<String> variableNames = new ArrayList<>();
      PsiClass recordClass = PsiUtil.resolveClassInClassTypeOnly(TypeConversionUtil.erasure(recordType));
      if (recordClass == null || !recordClass.isRecord()) return null;
      for (PsiRecordComponent recordComponent : recordClass.getRecordComponents()) {
        String nextName = new VariableNameGenerator(block, VariableKind.LOCAL_VARIABLE)
          .byName(recordComponent.getName())
          .skipNames(variableNames)
          .generate(false);
        variableNames.add(nextName);
      }
      for (List<PsiType> branch : branches.getValue()) {
        StringJoiner joiner = new StringJoiner(", ", "(", ")");
        if (branch.size() != variableNames.size()) return null;
        for (int i = 0; i < branch.size(); i++) {
          PsiType psiType = branch.get(i);
          String typeText = shorten ? psiType.getPresentableText() : psiType.getCanonicalText();
          joiner.add(typeText + " " + variableNames.get(i));
        }
        result.add(recordTypeString + joiner);
      }
    }
    //every item is equal, but it is needed to produce a stable result
    Collections.sort(result);
    return result;
  }
}
