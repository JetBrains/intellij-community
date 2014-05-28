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

/*
 * User: anna
 * Date: 19-Dec-2007
 */
package com.intellij.codeInspection;

import com.intellij.codeInspection.lang.HTMLComposerExtension;
import com.intellij.codeInspection.reference.RefClass;
import com.intellij.codeInspection.reference.RefMethod;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.Key;

public abstract class HTMLJavaHTMLComposer implements HTMLComposerExtension<HTMLJavaHTMLComposer> {
  public static final Key<HTMLJavaHTMLComposer> COMPOSER = Key.create("HTMLJavaComposer");

  public abstract void appendClassOrInterface(StringBuffer buf, RefClass refClass, boolean capitalizeFirstLetter);

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

  public abstract void appendClassExtendsImplements(StringBuffer buf, RefClass refClass);

  public abstract void appendDerivedClasses(StringBuffer buf, RefClass refClass);

  public abstract void appendLibraryMethods(StringBuffer buf, RefClass refClass);

  public abstract void appendSuperMethods(StringBuffer buf, RefMethod refMethod);

  public abstract void appendDerivedMethods(StringBuffer buf, RefMethod refMethod);

  public abstract void appendTypeReferences(StringBuffer buf, RefClass refClass);

  @Override
  public Key<HTMLJavaHTMLComposer> getID() {
    return COMPOSER;
  }

  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }
}