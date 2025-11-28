// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hint.api.impls;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.hint.api.LightJavaParameterInfo;
import com.intellij.codeInsight.hint.api.LightJavaParameterPresentation;
import com.intellij.codeInsight.hint.api.LightJavaSignaturePresentation;
import com.intellij.lang.parameterInfo.ParameterInfoUtils;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Range;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApiStatus.Internal
final class AnnotationParameterInfoUtil {
  private AnnotationParameterInfoUtil() { }

  private static @Nullable UIModel getUIModel(@NotNull PsiFile file,
                                              int offset,
                                              @NotNull List<PsiAnnotationMethod> methodList,
                                              PsiNameValuePair @NotNull [] attributes) {
    Map<String, PsiAnnotationMethod> nameToMethod = new HashMap<>();
    for (int i = 0; i < methodList.size(); i++) {
      PsiAnnotationMethod method = methodList.get(i);
      nameToMethod.put(method.getName(), method);
    }

    List<PsiAnnotationMethod> rearrangedMethodList = new ArrayList<>(attributes.length);
    for (PsiNameValuePair pair : attributes) {
      PsiAnnotationMethod method = nameToMethod.remove(pair.getAttributeName());
      if (method == null) return null;
      rearrangedMethodList.add(method);
    }
    for (int i = 0; i < methodList.size(); i++) {
      String name = methodList.get(i).getName();
      if (nameToMethod.containsKey(name)) {
        rearrangedMethodList.add(methodList.get(i));
      }
    }

    PsiAnnotationMethod method = findAnnotationMethod(file, offset);
    int index = rearrangedMethodList.indexOf(method);
    if (index == -1) {
      index = attributes.length;
    }
    int currentParameterIndex = index;
    UIModel result = new UIModel(rearrangedMethodList, currentParameterIndex);
    return result;
  }

  static @Nullable LightJavaParameterInfo createLightJavaParameterInfo(@NotNull PsiFile file,
                                                                       int offset,
                                                                       @NotNull List<PsiAnnotationMethod> methodList,
                                                                       PsiNameValuePair @NotNull [] attributes) {
    UIModel model = getUIModel(file, offset, methodList, attributes);
    if (model == null) return null;

    StringBuilder buffer = new StringBuilder();
    List<PsiAnnotationMethod> rearrangedList = model.rearrangedList();
    List<Range<Integer>> rangeList = new ArrayList<>(rearrangedList.size());

    for (int i = 0; i < rearrangedList.size(); i++) {
      int startOffset = buffer.length();
      PsiAnnotationMethod method = rearrangedList.get(i);
      PsiType type = method.getReturnType();
      if (type == null) return null;
      buffer.append(type.getPresentableText()).append(" ").append(method.getName());
      PsiAnnotationMemberValue value = method.getDefaultValue();
      if (value != null) {
        buffer.append(" = ").append(value.getText());
      }
      int endOffset = buffer.length();
      rangeList.add(new Range<>(startOffset, endOffset));
      if (i < rearrangedList.size() - 1) {
        buffer.append(", ");
      }
    }

    if (rearrangedList.isEmpty()) {
      buffer.append(CodeInsightBundle.message("parameter.info.no.parameters"));
    }

    return new LightJavaParameterInfo(List.of(
      new LightJavaSignaturePresentation(
        buffer.toString(),
        ContainerUtil.map(rangeList, range -> new LightJavaParameterPresentation(range, null)),
        null
      )
    ), 0, model.currentParameterIndex());
  }

  static PsiAnnotationMethod findAnnotationMethod(PsiFile file, int offset) {
    PsiNameValuePair pair = findNameValuePair(file, offset);
    if (pair == null) return null;
    final PsiReference reference = pair.getReference();
    final PsiElement resolved = reference != null ? reference.resolve() : null;
    return PsiUtil.isAnnotationMethod(resolved) ? (PsiAnnotationMethod)resolved : null;
  }

  private static @Nullable PsiNameValuePair findNameValuePair(PsiFile file, int offset) {
    PsiElement currentElement = file.findElementAt(offset);
    PsiNameValuePair pair = ParameterInfoUtils.findParentOfType(currentElement, PsiNameValuePair.class);

    if (pair != null) return pair;
    PsiElement candidate = PsiTreeUtil.skipWhitespacesBackward(currentElement);
    if (candidate instanceof PsiNameValuePair candidatePair) return candidatePair;

    candidate = PsiTreeUtil.skipWhitespacesForward(currentElement);
    return candidate instanceof PsiNameValuePair candidatePair ? candidatePair : null;
  }

  private record UIModel(List<PsiAnnotationMethod> rearrangedList, int currentParameterIndex) {
  }
}