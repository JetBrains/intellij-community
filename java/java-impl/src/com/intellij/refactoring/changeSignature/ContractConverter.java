// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.changeSignature;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.InferredAnnotationsManagerImpl;
import com.intellij.codeInspection.dataFlow.ContractReturnValue;
import com.intellij.codeInspection.dataFlow.JavaMethodContractUtil;
import com.intellij.codeInspection.dataFlow.MutationSignature;
import com.intellij.codeInspection.dataFlow.StandardMethodContract;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiMethod;
import one.util.streamex.IntStreamEx;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

final class ContractConverter {
  private ContractConverter() {}

  @Nullable
  static PsiAnnotation convertContract(PsiMethod method, JavaChangeInfo info) throws ContractConversionException {
    PsiAnnotation annotation = JavaMethodContractUtil.findContractAnnotation(method);
    if (annotation == null || AnnotationUtil.isInferredAnnotation(annotation)) return null;
    if (AnnotationUtil.isExternalAnnotation(annotation)) {
      throw new ContractConversionException("automatic update of external annotation is not yet supported");
    }
    if (annotation.getOwner() != method.getModifierList()) {
      throw new ContractConversionException("annotation is inherited from base method");
    }
    if (annotation.findDeclaredAttributeValue(MutationSignature.ATTR_MUTATES) != null) {
      throw new ContractConversionException("it contains mutation contract");
    }
    String text = AnnotationUtil.getStringAttributeValue(annotation, null);
    List<StandardMethodContract> contracts = Collections.emptyList();
    if (text != null) {
      try {
        contracts = StandardMethodContract.parseContract(text);
      }
      catch (StandardMethodContract.ParseException exception) {
        throw new ContractConversionException("error in contract definition: " + exception.getMessage());
      }
    }
    String[] oldParameterNames = info.getOldParameterNames();
    JavaParameterInfo[] newParameters = info.getNewParameters();
    int[] newToOldIndex = StreamEx.of(newParameters).mapToInt(ParameterInfo::getOldIndex).toArray();
    int[] oldToNewIndex = reverseIndex(oldParameterNames.length, newToOldIndex);

    List<StandardMethodContract> result = new ArrayList<>();
    for (StandardMethodContract contract : contracts) {
      result.add(convertContract(contract, newToOldIndex, oldToNewIndex, oldParameterNames));
    }
    if (result.equals(contracts)) return annotation;
    boolean pure = Boolean.TRUE.equals(AnnotationUtil.getBooleanAttributeValue(annotation, "pure"));
    String mutates = StringUtil.notNullize(AnnotationUtil.getStringAttributeValue(annotation, MutationSignature.ATTR_MUTATES));
    String resultValue = StreamEx.of(result).joining("; ");
    Project project = method.getProject();
    return InferredAnnotationsManagerImpl.createContractAnnotation(project, pure, resultValue, mutates);
  }

  @NotNull
  private static StandardMethodContract convertContract(@NotNull StandardMethodContract contract,
                                                        @NotNull int[] newToOldIndex,
                                                        @NotNull int[] oldToNewIndex,
                                                        @NotNull String[] oldParameterNames) throws ContractConversionException {
    if (contract.getParameterCount() != oldToNewIndex.length) {
      // invalid contract
      throw new ContractConversionException("invalid contract clause '" + contract + "'");
    }
    for (int i = 0; i < contract.getParameterCount(); i++) {
      if (contract.getParameterConstraint(i) != StandardMethodContract.ValueConstraint.ANY_VALUE && oldToNewIndex[i] == -1) {
        throw new ContractConversionException(
          "parameter '" + oldParameterNames[i] + "' was deleted, but contract clause '" + contract + "' depends on it");
      }
    }
    StandardMethodContract.ValueConstraint[] newConstraints = IntStreamEx.of(newToOldIndex)
                                                                         .mapToObj(idx -> idx == -1 ? StandardMethodContract.ValueConstraint.ANY_VALUE : contract.getParameterConstraint(idx))
                                                                         .toArray(StandardMethodContract.ValueConstraint.class);
    ContractReturnValue returnValue = contract.getReturnValue();
    if (returnValue instanceof ContractReturnValue.ParameterReturnValue) {
      int oldIndex = ((ContractReturnValue.ParameterReturnValue)returnValue).getParameterNumber();
      if (oldIndex >= contract.getParameterCount()) {
        throw new ContractConversionException("invalid reference in return value: " + returnValue);
      }
      int index = oldToNewIndex[oldIndex];
      if (index == -1) {
        throw new ContractConversionException("parameter '" + oldParameterNames[oldIndex] + "' was deleted, but contract clause '" + contract + "' returns it");
      }
      returnValue = ContractReturnValue.returnParameter(index);
    }
    return new StandardMethodContract(newConstraints, returnValue);
  }

  private static int[] reverseIndex(int oldParameterCount, int[] newToOldIndex) {
    int[] oldToNewIndex = new int[oldParameterCount];
    Arrays.fill(oldToNewIndex, -1);
    for (int i = 0; i < newToOldIndex.length; i++) {
      int oldIndex = newToOldIndex[i];
      if (oldIndex >= 0 && oldIndex < oldParameterCount) {
        oldToNewIndex[oldIndex] = i;
      }
    }
    return oldToNewIndex;
  }

  static final class ContractConversionException extends Exception {
    public ContractConversionException(String message) {
      super(message);
    }
  }
}
