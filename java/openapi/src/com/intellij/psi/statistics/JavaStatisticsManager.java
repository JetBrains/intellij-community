// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.statistics;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;


public abstract class JavaStatisticsManager {
  private static final Logger LOG = Logger.getInstance(JavaStatisticsManager.class);
  @NonNls public static final String CLASS_PREFIX = "class#";

  @NotNull
  private static StatisticsInfo createVariableUseInfo(final String name, final VariableKind variableKind,
                                                      final String propertyName,
                                                      final PsiType type) {
    String key1 = getVariableNameUseKey1(propertyName, type);
    String key2 = getVariableNameUseKey2(variableKind, name);
    return new StatisticsInfo(key1, key2);
  }

  @NotNull
  private static String getVariableNameUseKey1(String propertyName, PsiType type) {
    @NonNls StringBuilder buffer = new StringBuilder();
    buffer.append("variableName#");
    if (propertyName != null){
      buffer.append(propertyName);
    }
    buffer.append("#");
    if (type != null){
      buffer.append(type.getCanonicalText());
    }
    return buffer.toString();
  }

  @NotNull
  private static String getVariableNameUseKey2(VariableKind kind, String name) {
    return kind + "#" + name;
  }

  public static int getVariableNameUseCount(String name, VariableKind variableKind, String propertyName, PsiType type) {
    return StatisticsManager.getInstance().getUseCount(createVariableUseInfo(name, variableKind, propertyName, type));
  }

  public static void incVariableNameUseCount(String name, VariableKind variableKind, String propertyName, PsiType type) {
    StatisticsManager.getInstance().incUseCount(createVariableUseInfo(name, variableKind, propertyName, type));
  }

  @Nullable
  public static String getName(@NotNull String key2){
    final int startIndex = key2.indexOf('#');
    LOG.assertTrue(startIndex >= 0);
    @NonNls String s = key2.substring(0, startIndex);
    if(!"variableName".equals(s)) return null;
    final int index = key2.indexOf('#', startIndex + 1);
    LOG.assertTrue(index >= 0);
    return key2.substring(index + 1);
  }

  @NotNull
  private static VariableKind getVariableKindFromKey2(@NotNull String key2){
    int index = key2.indexOf('#');
    LOG.assertTrue(index >= 0);
    String s = key2.substring(0, index);
    return VariableKind.valueOf(s);
  }

  @NotNull
  private static String getVariableNameFromKey2(@NotNull String key2){
    int index = key2.indexOf('#');
    LOG.assertTrue(index >= 0);
    return key2.substring(index + 1);
  }

  @NonNls
  @NotNull
  public static String getMemberUseKey1(@Nullable PsiType qualifierType) {
    qualifierType = TypeConversionUtil.erasure(qualifierType);
    return "member#" + (qualifierType == null ? "" : qualifierType.getCanonicalText());
  }

  @NonNls
  @NotNull
  public static String getMemberUseKey2(@NotNull PsiMember member) {
    if (member instanceof PsiMethod){
      PsiMethod method = (PsiMethod)member;
      @NonNls StringBuilder buffer = new StringBuilder();
      buffer.append("method#");
      buffer.append(method.getName());
      for (PsiParameter param : method.getParameterList().getParameters()) {
        buffer.append("#");
        buffer.append(param.getType().getPresentableText());
      }
      return buffer.toString();
    }

    if (member instanceof PsiField){
      return "field#" + member.getName();
    }

    if (member instanceof PsiRecordComponent) {
      return "record#" + member.getName();
    }
    return CLASS_PREFIX + ((PsiClass)member).getQualifiedName();
  }

  @NotNull
  public static StatisticsInfo createInfo(@Nullable final PsiType qualifierType, @NotNull PsiMember member) {
    return new StatisticsInfo(getMemberUseKey1(qualifierType), getMemberUseKey2(member));
  }

  @NotNull
  public static StatisticsInfo createInfoForNoArgMethod(@NotNull String className, @NotNull String methodName) {
    return new StatisticsInfo("member#" + className, "method#" + methodName);
  }

  public static String @NotNull [] getAllVariableNamesUsed(VariableKind variableKind, String propertyName, PsiType type) {
    StatisticsInfo[] keys2 = StatisticsManager.getInstance().getAllValues(getVariableNameUseKey1(propertyName, type));

    List<String> list = new ArrayList<>();

    for (StatisticsInfo key2 : keys2) {
      VariableKind variableKind1 = getVariableKindFromKey2(key2.getValue());
      if (variableKind1 != variableKind) continue;
      String name = getVariableNameFromKey2(key2.getValue());
      list.add(name);
    }

    return ArrayUtilRt.toStringArray(list);
  }

  @NotNull @NonNls
  public static String getAfterNewKey(@Nullable PsiType expectedType) {
    return getMemberUseKey1(expectedType) + "###smartAfterNew";
  }

}
