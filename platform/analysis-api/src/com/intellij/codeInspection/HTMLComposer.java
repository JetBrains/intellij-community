// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInspection.reference.RefElement;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public abstract class HTMLComposer {
  public abstract void appendElementReference(@NotNull StringBuilder buf, RefElement refElement, String linkText, @NonNls String frameName);

  public abstract void appendElementReference(@NotNull StringBuilder buf, String url, String linkText, @NonNls String frameName);

  public abstract void appendElementInReferences(@NotNull StringBuilder buf, RefElement refElement);

  public abstract void appendElementOutReferences(@NotNull StringBuilder buf, RefElement refElement);

  public abstract void appendElementReference(@NotNull StringBuilder buf, RefElement refElement);

  public abstract void appendListItem(@NotNull StringBuilder buf, RefElement refElement);

  public static void appendHeading(@NotNull StringBuilder buf, String name){
    buf.append("<p class=\"problem-description-group\">")
       .append(name)
       .append("</p>");
  }

  public abstract void appendElementReference(@NotNull StringBuilder buf, RefElement refElement, boolean isPackageIncluded);

  public abstract String composeNumereables(int n, String statement, String singleEnding, String multipleEnding);

  public abstract void startList(@NotNull StringBuilder buf);

  public abstract void doneList(@NotNull StringBuilder buf);

  public abstract void startListItem(@NotNull StringBuilder buf);

  /**
   * @deprecated Use CSS for indentations
   */
  @Deprecated
  public static void appendAfterHeaderIndention(@NotNull StringBuilder buf) {
    buf.append("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;");
  }

  public abstract void appendNoProblems(@NotNull StringBuilder buf);

  public abstract <T> T getExtension(Key<T> key);
}
