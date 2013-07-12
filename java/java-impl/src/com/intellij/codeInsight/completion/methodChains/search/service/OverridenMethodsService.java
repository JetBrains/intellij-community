package com.intellij.codeInsight.completion.methodChains.search.service;

import com.google.common.collect.Multiset;
import com.intellij.compilerOutputIndex.impl.MethodIncompleteSignature;
import com.intellij.compilerOutputIndex.impl.callingLocation.CallingLocation;
import com.intellij.compilerOutputIndex.impl.callingLocation.MethodCallingLocationIndex;
import com.intellij.compilerOutputIndex.impl.callingLocation.MethodNameAndQualifier;
import com.intellij.compilerOutputIndex.impl.callingLocation.VariableType;
import com.intellij.compilerOutputIndex.impl.quickInheritance.QuickInheritanceIndex;
import com.intellij.compilerOutputIndex.impl.quickInheritance.QuickMethodsIndex;
import com.intellij.compilerOutputIndex.impl.quickInheritance.QuickOverrideUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;

/**
 * @author Dmitry Batkovich
 */
public class OverridenMethodsService {

  private final QuickMethodsIndex myQuickMethodsIndex;
  private final QuickInheritanceIndex myQuickInheritanceIndex;
  private final MethodCallingLocationIndex myMethodCallingLocationIndex;

  public OverridenMethodsService(final Project project) {
    myQuickInheritanceIndex = QuickInheritanceIndex.getInstance(project);
    myQuickMethodsIndex = QuickMethodsIndex.getInstance(project);
    myMethodCallingLocationIndex = MethodCallingLocationIndex.getInstance(project);
  }

  public boolean isMethodOverriden(final String classQName, final String methodName) {
    return QuickOverrideUtil.isMethodOverriden(classQName, methodName, myQuickInheritanceIndex, myQuickMethodsIndex);
  }

  public Pair<Integer, Integer> getMethodsUsageInOverridenContext(final MethodNameAndQualifier method) {
    final Multiset<MethodIncompleteSignature> locationsAsParam = myMethodCallingLocationIndex.getLocationsAsParam(method);
    int overridenOccurrences = 0;
    int nonOverridenOccurrences = 0;
    for (final Multiset.Entry<MethodIncompleteSignature> e : locationsAsParam.entrySet()) {
      final MethodIncompleteSignature sign = e.getElement();
      final boolean methodOverriden = isMethodOverriden(sign.getOwner(), sign.getName());
      if (methodOverriden) {
        overridenOccurrences++;
      }
      else {
        nonOverridenOccurrences++;
      }
    }

    return Pair.create(overridenOccurrences, nonOverridenOccurrences);
  }

  public Pair<Integer, Integer> getMethodUsageInFieldContext(final MethodNameAndQualifier method) {
    int asField = 0;
    int notField = 0;
    for (final CallingLocation callingLocation : myMethodCallingLocationIndex.getAllLocations(method)) {
      if (callingLocation.getVariableType().equals(VariableType.FIELD)) {
        asField++;
      }
      else {
        notField++;
      }
    }
    return Pair.create(asField, notField);
  }
}
