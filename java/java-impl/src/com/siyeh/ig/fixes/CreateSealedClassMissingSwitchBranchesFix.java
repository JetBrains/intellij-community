// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Function;

public class CreateSealedClassMissingSwitchBranchesFix extends CreateMissingSwitchBranchesFix {
  @NotNull
  private final List<String> myAllNames;

  public CreateSealedClassMissingSwitchBranchesFix(@NotNull PsiSwitchBlock block, Set<String> names, @NotNull List<String> allNames) {
    super(block, names);
    myAllNames = allNames;
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getFamilyName() {
    return InspectionGadgetsBundle.message("create.missing.sealed.class.switch.branches.fix.family.name");
  }

  @Override
  protected @NotNull List<String> getAllNames(@NotNull PsiClass ignored, @NotNull PsiSwitchBlock switchBlock) {
    Map<String, String> mapToConvert = getConversionNewTypeWithGeneric(switchBlock);
    List<String> result = new ArrayList<>();
    for (String name : myAllNames) {
      if (mapToConvert.containsKey(name)) {
        result.add(mapToConvert.get(name));
      }
      else {
        result.add(name);
      }
    }
    return result;
  }

  @NotNull
  private Map<String, String> getConversionNewTypeWithGeneric(@NotNull PsiSwitchBlock switchBlock) {
    HashMap<String, String> mapToConvert = new HashMap<>();
    PsiExpression expression = switchBlock.getExpression();
    if (expression == null) {
      return Map.of();
    }
    PsiType expressionType = expression.getType();
    if (!(expressionType instanceof PsiClassType expressionClassType)) {
      return Map.of();
    }
    PsiClassType.ClassResolveResult classResolveResult = expressionClassType.resolveGenerics();
    PsiClass expressionClass = classResolveResult.getElement();
    PsiSubstitutor superSubstitutor = classResolveResult.getSubstitutor();
    if (expressionClass == null) {
      return Map.of();
    }

    for (String myName : myNames) {
      Project project = switchBlock.getProject();
      PsiClass[] classes = JavaPsiFacade.getInstance(project).findClasses(myName, GlobalSearchScope.projectScope(project));
      if (classes.length != 1) {
        continue;
      }
      PsiClass classToAdd = classes[0];
      if (!classToAdd.hasTypeParameters()) {
        continue;
      }
      if (!InheritanceUtil.isInheritorOrSelf(classToAdd, expressionClass, true)) {
        return Map.of();
      }
      PsiSubstitutor inheritorSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(expressionClass, classToAdd, PsiSubstitutor.EMPTY);
      PsiSubstitutor targetSubstitutor = PsiSubstitutor.EMPTY;
      for (Map.Entry<PsiTypeParameter, PsiType> entry : inheritorSubstitutor.getSubstitutionMap().entrySet()) {
        PsiType value = entry.getValue();
        PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(value);
        if (!(psiClass instanceof PsiTypeParameter derivedTypeParameter)) {
          continue;
        }
        PsiType substituted = superSubstitutor.substitute(entry.getKey());
        targetSubstitutor = targetSubstitutor.put(derivedTypeParameter, substituted);
      }
      for (PsiTypeParameter parameter : classToAdd.getTypeParameters()) {
        if (targetSubstitutor.getSubstitutionMap().containsKey(parameter)) {
          continue;
        }
        targetSubstitutor = targetSubstitutor.put(parameter, PsiWildcardType.createUnbounded(parameter.getManager()));
      }

      PsiClassType classTypeToAdd = PsiElementFactory.getInstance(project).createType(classToAdd, targetSubstitutor);
      if (TypeConversionUtil.isAssignable(expressionClassType, classTypeToAdd)) {
        mapToConvert.put(myName, classTypeToAdd.getCanonicalText());
      }
      else {
        return Map.of();
      }
    }
    return mapToConvert;
  }

  @Override
  protected @NotNull Set<String> getNames(@NotNull PsiSwitchBlock switchBlock) {
    Map<String, String> mapToConvert = getConversionNewTypeWithGeneric(switchBlock);
    Set<String> result = new LinkedHashSet<>();
    for (String name : myNames) {
      if (mapToConvert.containsKey(name)) {
        result.add(mapToConvert.get(name));
      }
      else {
        result.add(name);
      }
    }
    return result;
  }

  @Override
  protected @NotNull Function<PsiSwitchLabelStatementBase, List<String>> getCaseExtractor() {
    return label -> {
      PsiCaseLabelElementList list = label.getCaseLabelElementList();
      if (list == null) return Collections.emptyList();
      return ContainerUtil.map(list.getElements(), PsiCaseLabelElement::getText);
    };
  }
}
