/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.unusedImport.UnusedImportLocalInspection;
import com.intellij.ide.highlighter.JavaHighlightingColors;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import org.jetbrains.annotations.NotNull;

public final class JavaHighlightInfoTypes {
  private JavaHighlightInfoTypes() {
  }
  
  public final static HighlightInfoType UNUSED_IMPORT = new HighlightInfoType.HighlightInfoTypeSeverityByKey(
    HighlightDisplayKey.findOrRegister(UnusedImportLocalInspection.SHORT_NAME, UnusedImportLocalInspection.DISPLAY_NAME), CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES);

  public final static HighlightInfoType JAVA_KEYWORD = new HighlightInfoType.HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, JavaHighlightingColors.KEYWORD);

  public final static HighlightInfoType CLASS_NAME = createSymbolTypeInfo(JavaHighlightingColors.CLASS_NAME_ATTRIBUTES);
  public final static HighlightInfoType LOCAL_VARIABLE = createSymbolTypeInfo(JavaHighlightingColors.LOCAL_VARIABLE_ATTRIBUTES);
  public final static HighlightInfoType INSTANCE_FIELD = createSymbolTypeInfo(JavaHighlightingColors.INSTANCE_FIELD_ATTRIBUTES);
  public final static HighlightInfoType INSTANCE_FINAL_FIELD = createSymbolTypeInfo(JavaHighlightingColors.INSTANCE_FINAL_FIELD_ATTRIBUTES);
  public final static HighlightInfoType STATIC_FIELD = createSymbolTypeInfo(JavaHighlightingColors.STATIC_FIELD_ATTRIBUTES);
  public final static HighlightInfoType STATIC_FINAL_FIELD = createSymbolTypeInfo(JavaHighlightingColors.STATIC_FINAL_FIELD_ATTRIBUTES);
  public final static HighlightInfoType PARAMETER = createSymbolTypeInfo(JavaHighlightingColors.PARAMETER_ATTRIBUTES);
  public final static HighlightInfoType LAMBDA_PARAMETER = createSymbolTypeInfo(JavaHighlightingColors.LAMBDA_PARAMETER_ATTRIBUTES);
  public final static HighlightInfoType METHOD_CALL = createSymbolTypeInfo(JavaHighlightingColors.METHOD_CALL_ATTRIBUTES);
  public final static HighlightInfoType METHOD_DECLARATION = createSymbolTypeInfo(JavaHighlightingColors.METHOD_DECLARATION_ATTRIBUTES);
  public final static HighlightInfoType CONSTRUCTOR_CALL = createSymbolTypeInfo(JavaHighlightingColors.CONSTRUCTOR_CALL_ATTRIBUTES);
  public final static HighlightInfoType CONSTRUCTOR_DECLARATION = createSymbolTypeInfo(JavaHighlightingColors.CONSTRUCTOR_DECLARATION_ATTRIBUTES);
  public final static HighlightInfoType STATIC_METHOD = createSymbolTypeInfo(JavaHighlightingColors.STATIC_METHOD_ATTRIBUTES);
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
  public final static HighlightInfoType REASSIGNED_LOCAL_VARIABLE
    = new HighlightInfoType.HighlightInfoTypeImpl(HighlightInfoType.SYMBOL_TYPE_SEVERITY, 
                                                  JavaHighlightingColors.REASSIGNED_LOCAL_VARIABLE_ATTRIBUTES);
  public final static HighlightInfoType REASSIGNED_PARAMETER
    = new HighlightInfoType.HighlightInfoTypeImpl(HighlightInfoType.SYMBOL_TYPE_SEVERITY, 
                                                  JavaHighlightingColors.REASSIGNED_PARAMETER_ATTRIBUTES);
  public final static HighlightInfoType IMPLICIT_ANONYMOUS_CLASS_PARAMETER
    = new HighlightInfoType.HighlightInfoTypeImpl(HighlightInfoType.SYMBOL_TYPE_SEVERITY,
                                                  JavaHighlightingColors.IMPLICIT_ANONYMOUS_CLASS_PARAMETER_ATTRIBUTES);
  
  private static HighlightInfoType createSymbolTypeInfo(@NotNull TextAttributesKey attributesKey) {
    return new HighlightInfoType.HighlightInfoTypeImpl(HighlightInfoType.SYMBOL_TYPE_SEVERITY, attributesKey, false);
  }
  
}
