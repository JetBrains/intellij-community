// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.pom.java.JavaLanguageFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated use {@link JavaLanguageFeature} and {@link PreviewFeatureUtil}.
 */
@Deprecated(forRemoval = true)
public enum HighlightingFeature {
  GENERICS(JavaLanguageFeature.GENERICS),
  ANNOTATIONS(JavaLanguageFeature.ANNOTATIONS),
  STATIC_IMPORTS(JavaLanguageFeature.STATIC_IMPORTS),
  FOR_EACH(JavaLanguageFeature.FOR_EACH),
  VARARGS(JavaLanguageFeature.VARARGS),
  HEX_FP_LITERALS(JavaLanguageFeature.HEX_FP_LITERALS),
  DIAMOND_TYPES(JavaLanguageFeature.DIAMOND_TYPES),
  MULTI_CATCH(JavaLanguageFeature.MULTI_CATCH),
  TRY_WITH_RESOURCES(JavaLanguageFeature.TRY_WITH_RESOURCES),
  BIN_LITERALS(JavaLanguageFeature.BIN_LITERALS),
  UNDERSCORES(JavaLanguageFeature.UNDERSCORES),
  EXTENSION_METHODS(JavaLanguageFeature.EXTENSION_METHODS),
  METHOD_REFERENCES(JavaLanguageFeature.METHOD_REFERENCES),
  LAMBDA_EXPRESSIONS(JavaLanguageFeature.LAMBDA_EXPRESSIONS),
  TYPE_ANNOTATIONS(JavaLanguageFeature.TYPE_ANNOTATIONS),
  RECEIVERS(JavaLanguageFeature.RECEIVERS),
  INTERSECTION_CASTS(JavaLanguageFeature.INTERSECTION_CASTS),
  STATIC_INTERFACE_CALLS(JavaLanguageFeature.STATIC_INTERFACE_CALLS),
  REFS_AS_RESOURCE(JavaLanguageFeature.REFS_AS_RESOURCE),
  MODULES(JavaLanguageFeature.MODULES),
  LVTI(JavaLanguageFeature.LVTI),
  VAR_LAMBDA_PARAMETER(JavaLanguageFeature.VAR_LAMBDA_PARAMETER),
  ENHANCED_SWITCH(JavaLanguageFeature.ENHANCED_SWITCH),
  SWITCH_EXPRESSION(JavaLanguageFeature.SWITCH_EXPRESSION),
  RECORDS(JavaLanguageFeature.RECORDS),
  PATTERNS(JavaLanguageFeature.PATTERNS),
  TEXT_BLOCK_ESCAPES(JavaLanguageFeature.TEXT_BLOCK_ESCAPES),
  TEXT_BLOCKS(JavaLanguageFeature.TEXT_BLOCKS),
  SEALED_CLASSES(JavaLanguageFeature.SEALED_CLASSES),
  LOCAL_INTERFACES(JavaLanguageFeature.LOCAL_INTERFACES),
  LOCAL_ENUMS(JavaLanguageFeature.LOCAL_ENUMS),
  INNER_STATICS(JavaLanguageFeature.INNER_STATICS),
  PARENTHESIZED_PATTERNS(JavaLanguageFeature.PARENTHESIZED_PATTERNS),
  PATTERNS_IN_SWITCH(JavaLanguageFeature.PATTERNS_IN_SWITCH),
  PATTERN_GUARDS_AND_RECORD_PATTERNS(JavaLanguageFeature.PATTERN_GUARDS_AND_RECORD_PATTERNS),
  RECORD_PATTERNS_IN_FOR_EACH(JavaLanguageFeature.RECORD_PATTERNS_IN_FOR_EACH),
  ENUM_QUALIFIED_NAME_IN_SWITCH(JavaLanguageFeature.ENUM_QUALIFIED_NAME_IN_SWITCH),
  STRING_TEMPLATES(JavaLanguageFeature.STRING_TEMPLATES),
  UNNAMED_PATTERNS_AND_VARIABLES(JavaLanguageFeature.UNNAMED_PATTERNS_AND_VARIABLES),
  IMPLICIT_CLASSES(JavaLanguageFeature.IMPLICIT_CLASSES),
  STATEMENTS_BEFORE_SUPER(JavaLanguageFeature.STATEMENTS_BEFORE_SUPER),
  ;

  private final JavaLanguageFeature myFeature;

  HighlightingFeature(@NotNull JavaLanguageFeature feature) {
    myFeature = feature;
  }

  public LanguageLevel getLevel() {
    return myFeature.getLevel();
  }

  /**
   * @param element a valid PsiElement to check (it's better to supply PsiFile if already known; any element is accepted for convenience)
   * @return true if this feature is available in the PsiFile the supplied element belongs to
   */
  public boolean isAvailable(@NotNull PsiElement element) {
    return myFeature.isAvailable(element);
  }
}
