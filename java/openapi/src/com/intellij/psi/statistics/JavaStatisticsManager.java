/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.statistics;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/**
 * @author yole
 */
public abstract class JavaStatisticsManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.statistics.JavaStatisticsManager");
  @NonNls public static final String CLASS_PREFIX = "class#";

  private static StatisticsInfo createVariableUseInfo(final String name, final VariableKind variableKind,
                                                      final String propertyName,
                                                      final PsiType type) {
    String key1 = getVariableNameUseKey1(propertyName, type);
    String key2 = getVariableNameUseKey2(variableKind, name);
    return new StatisticsInfo(key1, key2);
  }

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

  private static String getVariableNameUseKey2(VariableKind kind, String name) {
    StringBuilder buffer = new StringBuilder();
    buffer.append(kind);
    buffer.append("#");
    buffer.append(name);
    return buffer.toString();
  }

  public static int getVariableNameUseCount(String name, VariableKind variableKind, String propertyName, PsiType type) {
    return createVariableUseInfo(name, variableKind, propertyName, type).getUseCount();
  }

  public static void incVariableNameUseCount(String name, VariableKind variableKind, String propertyName, PsiType type) {
    createVariableUseInfo(name, variableKind, propertyName, type).incUseCount();
  }

  @Nullable
  public static String getName(String key2){
    final int startIndex = key2.indexOf("#");
    LOG.assertTrue(startIndex >= 0);
    @NonNls String s = key2.substring(0, startIndex);
    if(!"variableName".equals(s)) return null;
    final int index = key2.indexOf("#", startIndex + 1);
    LOG.assertTrue(index >= 0);
    return key2.substring(index + 1);
  }

  private static VariableKind getVariableKindFromKey2(String key2){
    int index = key2.indexOf("#");
    LOG.assertTrue(index >= 0);
    String s = key2.substring(0, index);
    return VariableKind.valueOf(s);
  }

  private static String getVariableNameFromKey2(String key2){
    int index = key2.indexOf("#");
    LOG.assertTrue(index >= 0);
    return key2.substring(index + 1);
  }

  @NonNls @NotNull
  public static String getMemberUseKey1(@Nullable PsiType qualifierType) {
    qualifierType = TypeConversionUtil.erasure(qualifierType);
    return "member#" + (qualifierType == null ? "" : qualifierType.getCanonicalText());
  }

  @NonNls
  public static String getMemberUseKey2(PsiMember member) {
    if (member instanceof PsiMethod){
      PsiMethod method = (PsiMethod)member;
      @NonNls StringBuilder buffer = new StringBuilder();
      buffer.append("method#");
      buffer.append(method.getName());
      for (PsiParameter parm : method.getParameterList().getParameters()) {
        buffer.append("#");
        buffer.append(parm.getType().getPresentableText());
      }
      return buffer.toString();
    }

    if (member instanceof PsiField){
      return "field#" + member.getName();
    }

    return CLASS_PREFIX + ((PsiClass)member).getQualifiedName();
  }

  public static StatisticsInfo createInfo(@Nullable final PsiType qualifierType, final PsiMember member) {
    return new StatisticsInfo(getMemberUseKey1(qualifierType), getMemberUseKey2(member));
  }

  public static String[] getAllVariableNamesUsed(VariableKind variableKind, String propertyName, PsiType type) {
    StatisticsInfo[] keys2 = StatisticsManager.getInstance().getAllValues(getVariableNameUseKey1(propertyName, type));

    ArrayList<String> list = new ArrayList<>();

    for (StatisticsInfo key2 : keys2) {
      VariableKind variableKind1 = getVariableKindFromKey2(key2.getValue());
      if (variableKind1 != variableKind) continue;
      String name = getVariableNameFromKey2(key2.getValue());
      list.add(name);
    }

    return ArrayUtil.toStringArray(list);
  }

  public static String getAfterNewKey(@Nullable PsiType expectedType) {
    return getMemberUseKey1(expectedType) + "###smartAfterNew";
  }

}
