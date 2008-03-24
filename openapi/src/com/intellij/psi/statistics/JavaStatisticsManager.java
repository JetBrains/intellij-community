package com.intellij.psi.statistics;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.psi.codeStyle.VariableKind;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author yole
 */
public abstract class JavaStatisticsManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.statistics.JavaStatisticsManager");
  protected static final int MAX_NAME_SUGGESTIONS_COUNT = 5;
  @NonNls public static final String CLASS_PREFIX = "class#";

  @Nullable
  public static NameContext getContext(final PsiElement element) {
    if(element instanceof PsiField){
      if(((PsiField)element).hasModifierProperty(PsiModifier.STATIC) && ((PsiField)element).hasModifierProperty(PsiModifier.FINAL))
        return NameContext.CONSTANT_NAME;
      return NameContext.FIELD_NAME;
    }
    if(element instanceof PsiLocalVariable) {
      return NameContext.LOCAL_VARIABLE_NAME;
    }
    return null;
  }

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
  private static NameContext getNameUsageContext(String key2){
    final int startIndex = key2.indexOf("#");
    LOG.assertTrue(startIndex >= 0);
    @NonNls String s = key2.substring(0, startIndex);
    if(!"variableName".equals(s)) return null;
    final int index = key2.indexOf("#", startIndex + 1);
    s = key2.substring(startIndex + 1, index);
    return NameContext.valueOf(s);
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
  public static String getMemberUseKey1(PsiType qualifierType) {
    qualifierType = TypeConversionUtil.erasure(qualifierType);
    return "member#" + (qualifierType == null ? "" : qualifierType.getCanonicalText());
  }

  @NonNls @NotNull
  public static String getNameUseKey1(PsiType qualifierType) {
    qualifierType = TypeConversionUtil.erasure(qualifierType);
    return "memberForName#" + (qualifierType == null ? "" : qualifierType.getCanonicalText());
  }

  @NonNls
  public static String getMemberUseKey2(PsiMember member) {
    if (member instanceof PsiMethod){
      PsiMethod method = (PsiMethod)member;
      @NonNls StringBuilder buffer = new StringBuilder();
      buffer.append("method#");
      buffer.append(method.getName());
      PsiParameter[] parms = method.getParameterList().getParameters();
      for (PsiParameter parm : parms) {
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

  public static StatisticsInfo createInfo(final PsiType qualifierType, final PsiMember member) {
    return new StatisticsInfo(getMemberUseKey1(qualifierType), getMemberUseKey2(member));
  }

  private static StatisticsInfo createNameUseInfo(final PsiType type, final NameContext context, final String name) {
    return new StatisticsInfo(getNameUseKey1(type), getNameUseKey(context, name));
  }

  private static String getNameUseKey(final NameContext context, final String name) {
    @NonNls final StringBuilder buffer = new StringBuilder();
    buffer.append("variableName#");
    buffer.append(context.name());
    buffer.append('#');
    buffer.append(name);
    return buffer.toString();
  }

  public static String[] getAllVariableNamesUsed(VariableKind variableKind, String propertyName, PsiType type) {
    StatisticsInfo[] keys2 = StatisticsManager.getInstance().getAllValues(getVariableNameUseKey1(propertyName, type));

    ArrayList<String> list = new ArrayList<String>();

    for (StatisticsInfo key2 : keys2) {
      VariableKind variableKind1 = getVariableKindFromKey2(key2.getValue());
      if (variableKind1 != variableKind) continue;
      String name = getVariableNameFromKey2(key2.getValue());
      list.add(name);
    }

    return list.toArray(new String[list.size()]);
  }

  public enum NameContext{
    LOCAL_VARIABLE_NAME,
    FIELD_NAME,
    CONSTANT_NAME
  }

  public static String[] getNameSuggestions(PsiType type, NameContext context, String prefix) {
    final List<String> suggestions = new ArrayList<String>();
    final String key1 = getNameUseKey1(type);

    final StatisticsInfo[] possibleNames = StatisticsManager.getInstance().getAllValues(key1);
    Arrays.sort(possibleNames);

    for (int i = 0; i < possibleNames.length && suggestions.size() < MAX_NAME_SUGGESTIONS_COUNT; i++) {
      final String key2 = possibleNames[i].getValue();
      if(context != getNameUsageContext(key2)) continue;
      final String name = getName(key2);
      if(name == null || !name.startsWith(prefix)) continue;
      suggestions.add(name);
    }
    return suggestions.toArray(new String[suggestions.size()]);
  }

  public static void incNameUseCount(PsiType type, NameContext context, String name) {
    createNameUseInfo(type, context, name).incUseCount();
  }
}
