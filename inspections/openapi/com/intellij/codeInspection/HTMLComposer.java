/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
 * Date: 08-Jan-2007
 */
package com.intellij.codeInspection;

import com.intellij.codeInspection.reference.RefClass;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefMethod;
import org.jetbrains.annotations.NonNls;

public abstract class HTMLComposer {
  public abstract void appendElementReference(StringBuffer buf, RefElement refElement, String linkText, @NonNls String frameName);

  public abstract void appendElementReference(StringBuffer buf, String url, String linkText, @NonNls String frameName);

  public abstract void appendElementInReferences(StringBuffer buf, RefElement refElement);

  public abstract void appendElementOutReferences(StringBuffer buf, RefElement refElement);

  public abstract void appendElementReference(StringBuffer buf, RefElement refElement);

  public abstract void appendListItem(StringBuffer buf, RefElement refElement);

  public static void appendHeading(@NonNls StringBuffer buf, String name){
    buf.append(
      "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<font style=\"font-family:verdana; font-weight:bold; color:#005555; size = 3\">");
    buf.append(name);
    buf.append(":</font>");
  }

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

  public abstract void appendElementReference(StringBuffer buf, RefElement refElement, boolean isPackageIncluded);

  public abstract String composeNumereables(int n, String statement, String singleEnding, String multipleEnding);

  public abstract void appendClassExtendsImplements(StringBuffer buf, RefClass refClass);

  public abstract void appendDerivedClasses(StringBuffer buf, RefClass refClass);

  public abstract void appendLibraryMethods(StringBuffer buf, RefClass refClass);

  public abstract void appendSuperMethods(StringBuffer buf, RefMethod refMethod);

  public abstract void appendDerivedMethods(StringBuffer buf, RefMethod refMethod);

  public abstract void appendTypeReferences(StringBuffer buf, RefClass refClass);

  public abstract void startList(@NonNls StringBuffer buf);

  public abstract void doneList(@NonNls StringBuffer buf);

  public abstract void startListItem(@NonNls StringBuffer buf);

  public static void appendAfterHeaderIndention(@NonNls StringBuffer buf) {
    buf.append("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;");
  }

  public abstract void appendNoProblems(StringBuffer buf);
}