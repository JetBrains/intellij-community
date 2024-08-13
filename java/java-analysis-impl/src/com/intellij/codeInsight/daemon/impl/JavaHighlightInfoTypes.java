// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.unusedImport.MissortedImportsInspection;
import com.intellij.codeInspection.unusedImport.UnusedImportInspection;
import com.intellij.ide.highlighter.JavaHighlightingColors;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class JavaHighlightInfoTypes {
  private JavaHighlightInfoTypes() {
  }
  
  public final static HighlightInfoType UNUSED_IMPORT = new HighlightInfoType.HighlightInfoTypeSeverityByKey(
    HighlightDisplayKey.findOrRegister(UnusedImportInspection.SHORT_NAME, UnusedImportInspection.getDisplayNameText()), CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES);
  @NonNls
  public static final HighlightInfoType MISSORTED_IMPORTS = new HighlightInfoType.HighlightInfoTypeSeverityByKey(
    HighlightDisplayKey.findOrRegister(MissortedImportsInspection.SHORT_NAME, MissortedImportsInspection.getDisplayNameText()), JavaHighlightingColors.MISSORTED_IMPORTS_ATTRIBUTES);

  public final static HighlightInfoType JAVA_KEYWORD = new HighlightInfoType.HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, JavaHighlightingColors.KEYWORD);
  public final static HighlightInfoType JAVA_KEYWORD_CLASS_FILE = new HighlightInfoType.HighlightInfoTypeImpl(HighlightInfoType.SYMBOL_TYPE_SEVERITY, JavaHighlightingColors.KEYWORD);

  public final static HighlightInfoType CLASS_NAME = createSymbolTypeInfo(JavaHighlightingColors.CLASS_NAME_ATTRIBUTES);
  public final static HighlightInfoType LOCAL_VARIABLE = createSymbolTypeInfo(JavaHighlightingColors.LOCAL_VARIABLE_ATTRIBUTES);
  public final static HighlightInfoType INSTANCE_FIELD = createSymbolTypeInfo(JavaHighlightingColors.INSTANCE_FIELD_ATTRIBUTES);
  public final static HighlightInfoType INSTANCE_FINAL_FIELD = createSymbolTypeInfo(JavaHighlightingColors.INSTANCE_FINAL_FIELD_ATTRIBUTES);
  public final static HighlightInfoType STATIC_FIELD = createSymbolTypeInfo(JavaHighlightingColors.STATIC_FIELD_ATTRIBUTES);
  public final static HighlightInfoType STATIC_FIELD_IMPORTED = createSymbolTypeInfo(JavaHighlightingColors.STATIC_FIELD_IMPORTED_ATTRIBUTES);
  public final static HighlightInfoType STATIC_FINAL_FIELD = createSymbolTypeInfo(JavaHighlightingColors.STATIC_FINAL_FIELD_ATTRIBUTES);
  public final static HighlightInfoType STATIC_FINAL_FIELD_IMPORTED = createSymbolTypeInfo(JavaHighlightingColors.STATIC_FINAL_FIELD_IMPORTED_ATTRIBUTES);
  public final static HighlightInfoType PARAMETER = createSymbolTypeInfo(JavaHighlightingColors.PARAMETER_ATTRIBUTES);
  public final static HighlightInfoType LAMBDA_PARAMETER = createSymbolTypeInfo(JavaHighlightingColors.LAMBDA_PARAMETER_ATTRIBUTES);
  public final static HighlightInfoType METHOD_CALL = createSymbolTypeInfo(JavaHighlightingColors.METHOD_CALL_ATTRIBUTES);
  public final static HighlightInfoType METHOD_DECLARATION = createSymbolTypeInfo(JavaHighlightingColors.METHOD_DECLARATION_ATTRIBUTES);
  public final static HighlightInfoType CONSTRUCTOR_CALL = createSymbolTypeInfo(JavaHighlightingColors.CONSTRUCTOR_CALL_ATTRIBUTES);
  public final static HighlightInfoType CONSTRUCTOR_DECLARATION = createSymbolTypeInfo(JavaHighlightingColors.CONSTRUCTOR_DECLARATION_ATTRIBUTES);
  public final static HighlightInfoType STATIC_METHOD = createSymbolTypeInfo(JavaHighlightingColors.STATIC_METHOD_ATTRIBUTES);
  public final static HighlightInfoType STATIC_METHOD_CALL_IMPORTED = createSymbolTypeInfo(JavaHighlightingColors.STATIC_METHOD_CALL_IMPORTED_ATTRIBUTES);
  public final static HighlightInfoType ABSTRACT_METHOD = createSymbolTypeInfo(JavaHighlightingColors.ABSTRACT_METHOD_ATTRIBUTES);
  public final static HighlightInfoType INHERITED_METHOD = createSymbolTypeInfo(JavaHighlightingColors.INHERITED_METHOD_ATTRIBUTES);
  public final static HighlightInfoType ANONYMOUS_CLASS_NAME = createSymbolTypeInfo(JavaHighlightingColors.ANONYMOUS_CLASS_NAME_ATTRIBUTES);
  public final static HighlightInfoType INTERFACE_NAME = createSymbolTypeInfo(JavaHighlightingColors.INTERFACE_NAME_ATTRIBUTES);
  public final static HighlightInfoType ENUM_NAME = createSymbolTypeInfo(JavaHighlightingColors.ENUM_NAME_ATTRIBUTES);
  public final static HighlightInfoType TYPE_PARAMETER_NAME 
    = new HighlightInfoType.HighlightInfoTypeImpl(HighlightInfoType.SYMBOL_TYPE_SEVERITY, 
                                                  JavaHighlightingColors.TYPE_PARAMETER_NAME_ATTRIBUTES);
  public final static HighlightInfoType ABSTRACT_CLASS_NAME = createSymbolTypeInfo(JavaHighlightingColors.ABSTRACT_CLASS_NAME_ATTRIBUTES);
  public final static HighlightInfoType ANNOTATION_NAME
    = new HighlightInfoType.HighlightInfoTypeImpl(HighlightInfoType.SYMBOL_TYPE_SEVERITY, 
                                                  JavaHighlightingColors.ANNOTATION_NAME_ATTRIBUTES);
  public final static HighlightInfoType ANNOTATION_ATTRIBUTE_NAME
    = new HighlightInfoType.HighlightInfoTypeImpl(HighlightInfoType.SYMBOL_TYPE_SEVERITY, 
                                                  JavaHighlightingColors.ANNOTATION_ATTRIBUTE_NAME_ATTRIBUTES);
  public final static HighlightInfoType IMPLICIT_ANONYMOUS_CLASS_PARAMETER
    = new HighlightInfoType.HighlightInfoTypeImpl(HighlightInfoType.SYMBOL_TYPE_SEVERITY,
                                                  JavaHighlightingColors.IMPLICIT_ANONYMOUS_CLASS_PARAMETER_ATTRIBUTES);

  @NotNull
  private static HighlightInfoType createSymbolTypeInfo(@NotNull TextAttributesKey attributesKey) {
    return new HighlightInfoType.HighlightInfoTypeImpl(HighlightInfoType.SYMBOL_TYPE_SEVERITY, attributesKey, false);
  }
}
