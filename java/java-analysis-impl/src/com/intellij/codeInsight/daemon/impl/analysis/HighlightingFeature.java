// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public enum HighlightingFeature {
  GENERICS(LanguageLevel.JDK_1_5, "feature.generics"),
  ANNOTATIONS(LanguageLevel.JDK_1_5, "feature.annotations"),
  STATIC_IMPORTS(LanguageLevel.JDK_1_5, "feature.static.imports"),
  FOR_EACH(LanguageLevel.JDK_1_5, "feature.for.each"),
  VARARGS(LanguageLevel.JDK_1_5, "feature.varargs"),
  HEX_FP_LITERALS(LanguageLevel.JDK_1_5, "feature.hex.fp.literals"),
  DIAMOND_TYPES(LanguageLevel.JDK_1_7, "feature.diamond.types"),
  MULTI_CATCH(LanguageLevel.JDK_1_7, "feature.multi.catch"),
  TRY_WITH_RESOURCES(LanguageLevel.JDK_1_7, "feature.try.with.resources"),
  BIN_LITERALS(LanguageLevel.JDK_1_7, "feature.binary.literals"),
  UNDERSCORES(LanguageLevel.JDK_1_7, "feature.underscores.in.literals"),
  EXTENSION_METHODS(LanguageLevel.JDK_1_8, "feature.extension.methods"),
  METHOD_REFERENCES(LanguageLevel.JDK_1_8, "feature.method.references"),
  LAMBDA_EXPRESSIONS(LanguageLevel.JDK_1_8, "feature.lambda.expressions"),
  TYPE_ANNOTATIONS(LanguageLevel.JDK_1_8, "feature.type.annotations"),
  RECEIVERS(LanguageLevel.JDK_1_8, "feature.type.receivers"),
  INTERSECTION_CASTS(LanguageLevel.JDK_1_8, "feature.intersections.in.casts"),
  STATIC_INTERFACE_CALLS(LanguageLevel.JDK_1_8, "feature.static.interface.calls"),
  REFS_AS_RESOURCE(LanguageLevel.JDK_1_9, "feature.try.with.resources.refs"),
  MODULES(LanguageLevel.JDK_1_9, "feature.modules"),
  LVTI(LanguageLevel.JDK_10, "feature.lvti"),
  VAR_LAMBDA_PARAMETER(LanguageLevel.JDK_11, "feature.var.lambda.parameter"),
  ENHANCED_SWITCH(LanguageLevel.JDK_13_PREVIEW, "feature.enhanced.switch"){
    @Override
    boolean isSufficient(@NotNull LanguageLevel useSiteLevel) {
      return useSiteLevel.isAtLeast(LanguageLevel.JDK_13_PREVIEW);//enabled in jdk 14 as standard
    }

    @Override
    LanguageLevel getStandardLevel() {
      return LanguageLevel.JDK_14;
    }
  },
  SWITCH_EXPRESSION(LanguageLevel.JDK_13_PREVIEW, "feature.switch.expressions") {
    @Override
    boolean isSufficient(@NotNull LanguageLevel useSiteLevel) {
      return useSiteLevel.isAtLeast(LanguageLevel.JDK_13_PREVIEW);//enabled in jdk 14 as standard
    }

    @Override
    LanguageLevel getStandardLevel() {
      return LanguageLevel.JDK_14;
    }
  },
  TEXT_BLOCKS(LanguageLevel.JDK_13_PREVIEW, "feature.text.blocks"),
  RECORDS(LanguageLevel.JDK_14_PREVIEW, "feature.records"),
  PATTERNS(LanguageLevel.JDK_14_PREVIEW, "feature.patterns.instanceof"),
  TEXT_BLOCK_ESCAPES(LanguageLevel.JDK_14_PREVIEW, "feature.text.block.escape.sequences");

  final LanguageLevel level;
  @PropertyKey(resourceBundle = JavaErrorBundle.BUNDLE)
  final String key;

  HighlightingFeature(@NotNull LanguageLevel level, @NotNull @PropertyKey(resourceBundle = JavaAnalysisBundle.BUNDLE) String key) {
    this.level = level;
    this.key = key;
  }

  /**
   * @param element a valid PsiElement to check (it's better to supply PsiFile if already known; any element is accepted for convenience)
   * @return true if this feature is available in the PsiFile the supplied element belongs to
   */
  public boolean isAvailable(@NotNull PsiElement element) {
    return isSufficient(PsiUtil.getLanguageLevel(element));
  }

  boolean isSufficient(@NotNull LanguageLevel useSiteLevel) {
    return useSiteLevel.isAtLeast(level) && (!level.isPreview() || useSiteLevel.isPreview());
  }

  /**
   * Override if feature was preview and then accepted as standard
   */
  LanguageLevel getStandardLevel() {
    return level.isPreview() ? null : level;
  }
}
