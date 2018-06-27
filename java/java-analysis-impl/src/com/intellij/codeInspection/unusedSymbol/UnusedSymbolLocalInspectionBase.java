// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.unusedSymbol;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.codeInspection.ex.UnfairLocalInspectionTool;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiModifier;
import org.intellij.lang.annotations.Pattern;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UnusedSymbolLocalInspectionBase extends AbstractBaseJavaLocalInspectionTool implements UnfairLocalInspectionTool {
  @NonNls public static final String SHORT_NAME = HighlightInfoType.UNUSED_SYMBOL_SHORT_NAME;
  @NonNls public static final String DISPLAY_NAME = HighlightInfoType.UNUSED_SYMBOL_DISPLAY_NAME;
  @NonNls public static final String UNUSED_PARAMETERS_SHORT_NAME = "UnusedParameters";
  @NonNls public static final String UNUSED_ID = "unused";

  public boolean LOCAL_VARIABLE = true;
  public boolean FIELD = true;
  public boolean METHOD = true;
  public boolean CLASS = true;
  protected boolean INNER_CLASS = true;
  public boolean PARAMETER = true;
  public boolean REPORT_PARAMETER_FOR_PUBLIC_METHODS = true;

  protected String myClassVisibility = PsiModifier.PUBLIC;
  protected String myInnerClassVisibility = PsiModifier.PUBLIC;
  protected String myFieldVisibility = PsiModifier.PUBLIC;
  protected String myMethodVisibility = PsiModifier.PUBLIC;
  protected String myParameterVisibility = PsiModifier.PUBLIC;
  private boolean myIgnoreAccessors = false;


  @PsiModifier.ModifierConstant
  @Nullable
  public String getClassVisibility() {
    if (!CLASS) return null;
    return myClassVisibility;
  }
  @PsiModifier.ModifierConstant
  @Nullable
  public String getFieldVisibility() {
    if (!FIELD) return null;
    return myFieldVisibility;
  }
  @PsiModifier.ModifierConstant
  @Nullable
  public String getMethodVisibility() {
    if (!METHOD) return null;
    return myMethodVisibility;
  }

  @PsiModifier.ModifierConstant
  @Nullable
  public String getParameterVisibility() {
    if (!PARAMETER) return null;
    return myParameterVisibility;
  }

  @PsiModifier.ModifierConstant
  @Nullable
  public String getInnerClassVisibility() {
    if (!INNER_CLASS) return null;
    return myInnerClassVisibility;
  }

  public void setInnerClassVisibility(String innerClassVisibility) {
    myInnerClassVisibility = innerClassVisibility;
  }

  public void setClassVisibility(String classVisibility) {
    this.myClassVisibility = classVisibility;
  }

  public void setFieldVisibility(String fieldVisibility) {
    this.myFieldVisibility = fieldVisibility;
  }

  public void setMethodVisibility(String methodVisibility) {
    this.myMethodVisibility = methodVisibility;
  }

  public void setParameterVisibility(String parameterVisibility) {
    REPORT_PARAMETER_FOR_PUBLIC_METHODS = PsiModifier.PUBLIC.equals(parameterVisibility);
    this.myParameterVisibility = parameterVisibility;
  }

  public boolean isIgnoreAccessors() {
    return myIgnoreAccessors;
  }

  public void setIgnoreAccessors(boolean ignoreAccessors) {
    myIgnoreAccessors = ignoreAccessors;
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.DECLARATION_REDUNDANCY;
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  @Override
  @NotNull
  @NonNls
  public String getShortName() {
    return SHORT_NAME;
  }

  @Override
  @Pattern(VALID_ID_PATTERN)
  @NotNull
  @NonNls
  public String getID() {
    return UNUSED_ID;
  }

  @Override
  public String getAlternativeID() {
    return UnusedDeclarationInspectionBase.ALTERNATIVE_ID;
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    writeVisibility(node, myClassVisibility, "klass");
    writeVisibility(node, myInnerClassVisibility, "inner_class");
    writeVisibility(node, myFieldVisibility, "field");
    writeVisibility(node, myMethodVisibility, "method");
    writeVisibility(node, "parameter", myParameterVisibility, getParameterDefaultVisibility());
    if (myIgnoreAccessors) {
      node.setAttribute("ignoreAccessors", Boolean.toString(true));
    }
    if (!INNER_CLASS) {
      node.setAttribute("INNER_CLASS", Boolean.toString(false));
    }
    super.writeSettings(node);
  }

  private static void writeVisibility(Element node, String visibility, String type) {
    writeVisibility(node, type, visibility, PsiModifier.PUBLIC);
  }

  private static void writeVisibility(Element node,
                                      String type,
                                      String visibility,
                                      String defaultVisibility) {
    if (!defaultVisibility.equals(visibility)) {
      node.setAttribute(type, visibility);
    }
  }

  private String getParameterDefaultVisibility() {
    return REPORT_PARAMETER_FOR_PUBLIC_METHODS ? PsiModifier.PUBLIC : PsiModifier.PRIVATE;
  }

  @Override
  public void readSettings(@NotNull Element node) throws InvalidDataException {
    super.readSettings(node);
    myClassVisibility = readVisibility(node, "klass");
    myInnerClassVisibility = readVisibility(node, "inner_class");
    myFieldVisibility = readVisibility(node, "field");
    myMethodVisibility = readVisibility(node, "method");
    myParameterVisibility = readVisibility(node, "parameter", getParameterDefaultVisibility());
    final String ignoreAccessors = node.getAttributeValue("ignoreAccessors");
    myIgnoreAccessors = ignoreAccessors != null && Boolean.parseBoolean(ignoreAccessors);
    final String innerClassEnabled = node.getAttributeValue("INNER_CLASS");
    INNER_CLASS = innerClassEnabled == null || Boolean.parseBoolean(innerClassEnabled);
  }

  private static String readVisibility(@NotNull Element node, final String type) {
    return readVisibility(node, type, PsiModifier.PUBLIC);
  }

  private static String readVisibility(@NotNull Element node,
                                       final String type,
                                       final String defaultVisibility) {
    final String visibility = node.getAttributeValue(type);
    if (visibility == null) {
      return defaultVisibility;
    }
    return visibility;
  }
}
