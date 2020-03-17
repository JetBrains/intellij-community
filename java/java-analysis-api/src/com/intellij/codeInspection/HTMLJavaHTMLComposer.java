// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInspection.lang.HTMLComposerExtension;
import com.intellij.codeInspection.reference.RefClass;
import com.intellij.codeInspection.reference.RefMethod;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

public abstract class HTMLJavaHTMLComposer implements HTMLComposerExtension<HTMLJavaHTMLComposer> {
  public static final Key<HTMLJavaHTMLComposer> COMPOSER = Key.create("HTMLJavaComposer");

  public abstract void appendClassOrInterface(@NotNull StringBuilder buf, RefClass refClass, boolean capitalizeFirstLetter);

  public static String getClassOrInterface(RefClass refClass, boolean capitalizeFirstLetter) {
    if (refClass.isInterface()) {
      return capitalizeFirstLetter ? InspectionsBundle.message("inspection.export.results.capitalized.interface") : InspectionsBundle.message("inspection.export.results.interface");
    }
    else if (refClass.isAbstract()) {
      return capitalizeFirstLetter ? InspectionsBundle.message("inspection.export.results.capitalized.abstract.class") : InspectionsBundle.message("inspection.export.results.abstract.class");
    }
    else {
      return capitalizeFirstLetter ? InspectionsBundle.message("inspection.export.results.capitalized.class") : InspectionsBundle.message("inspection.export.results.class");
    }
  }

  public abstract void appendClassExtendsImplements(@NotNull StringBuilder buf, RefClass refClass);

  public abstract void appendDerivedClasses(@NotNull StringBuilder buf, RefClass refClass);

  public abstract void appendLibraryMethods(@NotNull StringBuilder buf, RefClass refClass);

  public abstract void appendSuperMethods(@NotNull StringBuilder buf, RefMethod refMethod);

  public abstract void appendDerivedMethods(@NotNull StringBuilder buf, RefMethod refMethod);

  public abstract void appendTypeReferences(@NotNull StringBuilder buf, RefClass refClass);

  @Override
  public Key<HTMLJavaHTMLComposer> getID() {
    return COMPOSER;
  }

  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }
}