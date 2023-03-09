// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.regexp.inspection.custom;

import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class CustomRegExpInspectionToolWrapper extends LocalInspectionToolWrapper {

  CustomRegExpInspectionToolWrapper(RegExpInspectionConfiguration configuration) {
    super(new CustomRegExpFakeInspection(configuration));
  }

  public int hashCode() {
    return myTool.getShortName().hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return obj.getClass() == CustomRegExpInspectionToolWrapper.class &&
           ((CustomRegExpInspectionToolWrapper)obj).myTool.getShortName().equals(myTool.getShortName());
  }

  @NotNull
  @Override
  public LocalInspectionToolWrapper createCopy() {
    final CustomRegExpFakeInspection inspection = (CustomRegExpFakeInspection)getTool();
    RegExpInspectionConfiguration configuration = inspection.getConfiguration();
    return new CustomRegExpInspectionToolWrapper(configuration);
  }
}
