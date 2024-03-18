// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated use {@link JavaFeature} and {@link PreviewFeatureUtil}.
 */
@Deprecated(forRemoval = true)
public enum HighlightingFeature {
  GENERICS(JavaFeature.GENERICS),
  ANNOTATIONS(JavaFeature.ANNOTATIONS),
  STATIC_IMPORTS(JavaFeature.STATIC_IMPORTS),
  FOR_EACH(JavaFeature.FOR_EACH),
  VARARGS(JavaFeature.VARARGS),
  HEX_FP_LITERALS(JavaFeature.HEX_FP_LITERALS),
  DIAMOND_TYPES(JavaFeature.DIAMOND_TYPES),
  MULTI_CATCH(JavaFeature.MULTI_CATCH),
  TRY_WITH_RESOURCES(JavaFeature.TRY_WITH_RESOURCES),
  BIN_LITERALS(JavaFeature.BIN_LITERALS),
  UNDERSCORES(JavaFeature.UNDERSCORES),
  EXTENSION_METHODS(JavaFeature.EXTENSION_METHODS),
  METHOD_REFERENCES(JavaFeature.METHOD_REFERENCES),
  LAMBDA_EXPRESSIONS(JavaFeature.LAMBDA_EXPRESSIONS),
  TYPE_ANNOTATIONS(JavaFeature.TYPE_ANNOTATIONS),
  RECEIVERS(JavaFeature.RECEIVERS),
  INTERSECTION_CASTS(JavaFeature.INTERSECTION_CASTS),
  STATIC_INTERFACE_CALLS(JavaFeature.STATIC_INTERFACE_CALLS),
  REFS_AS_RESOURCE(JavaFeature.REFS_AS_RESOURCE),
  MODULES(JavaFeature.MODULES),
  LVTI(JavaFeature.LVTI),
  VAR_LAMBDA_PARAMETER(JavaFeature.VAR_LAMBDA_PARAMETER),
  ENHANCED_SWITCH(JavaFeature.ENHANCED_SWITCH),
  SWITCH_EXPRESSION(JavaFeature.SWITCH_EXPRESSION),
  RECORDS(JavaFeature.RECORDS),
  PATTERNS(JavaFeature.PATTERNS),
  TEXT_BLOCK_ESCAPES(JavaFeature.TEXT_BLOCK_ESCAPES),
  TEXT_BLOCKS(JavaFeature.TEXT_BLOCKS),
  SEALED_CLASSES(JavaFeature.SEALED_CLASSES),
  LOCAL_INTERFACES(JavaFeature.LOCAL_INTERFACES),
  LOCAL_ENUMS(JavaFeature.LOCAL_ENUMS),
  INNER_STATICS(JavaFeature.INNER_STATICS),
  PATTERNS_IN_SWITCH(JavaFeature.PATTERNS_IN_SWITCH),
  PATTERN_GUARDS_AND_RECORD_PATTERNS(JavaFeature.PATTERN_GUARDS_AND_RECORD_PATTERNS),
  RECORD_PATTERNS_IN_FOR_EACH(JavaFeature.RECORD_PATTERNS_IN_FOR_EACH),
  ENUM_QUALIFIED_NAME_IN_SWITCH(JavaFeature.ENUM_QUALIFIED_NAME_IN_SWITCH),
  STRING_TEMPLATES(JavaFeature.STRING_TEMPLATES),
  UNNAMED_PATTERNS_AND_VARIABLES(JavaFeature.UNNAMED_PATTERNS_AND_VARIABLES),
  IMPLICIT_CLASSES(JavaFeature.IMPLICIT_CLASSES),
  STATEMENTS_BEFORE_SUPER(JavaFeature.STATEMENTS_BEFORE_SUPER),
  ;

  private final JavaFeature myFeature;

  HighlightingFeature(@NotNull JavaFeature feature) {
    myFeature = feature;
  }

  public LanguageLevel getLevel() {
    return myFeature.getMinimumLevel();
  }

  /**
   * @param element a valid PsiElement to check (it's better to supply PsiFile if already known; any element is accepted for convenience)
   * @return true if this feature is available in the PsiFile the supplied element belongs to
   */
  public boolean isAvailable(@NotNull PsiElement element) {
    return PsiUtil.isAvailable(myFeature, element);
  }
}
