// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.codeInspection.lang.HTMLComposerExtension;
import com.intellij.codeInspection.reference.RefClass;
import com.intellij.codeInspection.reference.RefMethod;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.Strings;
import com.intellij.uast.UastMetaLanguage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;

public abstract class HTMLJavaHTMLComposer implements HTMLComposerExtension<HTMLJavaHTMLComposer> {
  public static final Key<HTMLJavaHTMLComposer> COMPOSER = Key.create("HTMLJavaComposer");

  public abstract void appendClassOrInterface(@NotNull StringBuilder buf, @NotNull RefClass refClass, boolean capitalizeFirstLetter);

  public static String getClassOrInterface(@NotNull RefClass refClass, boolean capitalizeFirstLetter) {
    String message;
    if (refClass.isAnnotationType()) {
      message = AnalysisBundle.message("inspection.export.results.annotation.type");
    }
    else if (refClass.isInterface()) {
      message = AnalysisBundle.message("inspection.export.results.interface");
    }
    else if (refClass.isAbstract()) {
      message = AnalysisBundle.message("inspection.export.results.abstract.class");
    }
    else if (refClass.isEnum()) {
      message = AnalysisBundle.message("inspection.export.results.enum.class");
    }
    else if (refClass.isRecord()) {
      message = AnalysisBundle.message("inspection.export.results.record.class");
    }
    else {
      message = AnalysisBundle.message("inspection.export.results.class");
    }
    return capitalizeFirstLetter ? Strings.capitalize(message) : message;
  }

  public abstract void appendClassExtendsImplements(@NotNull StringBuilder buf, @NotNull RefClass refClass);

  public abstract void appendDerivedClasses(@NotNull StringBuilder buf, @NotNull RefClass refClass);

  public abstract void appendLibraryMethods(@NotNull StringBuilder buf, @NotNull RefClass refClass);

  public abstract void appendSuperMethods(@NotNull StringBuilder buf, @NotNull RefMethod refMethod);

  public abstract void appendDerivedMethods(@NotNull StringBuilder buf, @NotNull RefMethod refMethod);

  public abstract void appendDerivedFunctionalExpressions(@NotNull StringBuilder buf, @NotNull RefMethod refMethod);

  public abstract void appendTypeReferences(@NotNull StringBuilder buf, @NotNull RefClass refClass);

  @Override
  public Key<HTMLJavaHTMLComposer> getID() {
    return COMPOSER;
  }

  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }

  @Override
  public @Unmodifiable @NotNull Collection<Language> getLanguages() {
    return Language.findInstance(UastMetaLanguage.class).getMatchingLanguages();
  }
}