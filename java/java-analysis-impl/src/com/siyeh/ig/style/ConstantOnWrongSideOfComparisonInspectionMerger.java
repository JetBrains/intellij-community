// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.codeInspection.ex.InspectionElementsMergerBase;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public final class ConstantOnWrongSideOfComparisonInspectionMerger extends InspectionElementsMergerBase {

  private static final @NonNls String CONSTANT_ON_LHS = "ConstantOnLHSOfComparison";
  private static final @NonNls String CONSTANT_ON_RHS = "ConstantOnRHSOfComparison";

  @Override
  public @NotNull String getMergedToolName() {
    return "ConstantOnWrongSideOfComparison";
  }

  @Override
  public String @NotNull [] getSourceToolNames() {
    return new String[] {
      CONSTANT_ON_LHS,
      CONSTANT_ON_RHS
    };
  }

  @Override
  public String @NotNull [] getSuppressIds() {
    return new String[] {
      "ConstantOnLeftSideOfComparison",
      "ConstantOnRightSideOfComparison"
    };
  }

  @Override
  protected boolean isEnabledByDefault(@NotNull String sourceToolName) {
    return false;
  }

  @Override
  protected boolean writeMergedContent(@NotNull Element toolElement) {
    // merged tool is not enabled by default, so always needs to be written when either of the source tools were enabled
    return Boolean.parseBoolean(toolElement.getAttributeValue("enabled", "false"));
  }

  @Override
  protected Element transformElement(@NotNull String sourceToolName, @NotNull Element sourceElement, @NotNull Element toolElement) {
    if (CONSTANT_ON_LHS.equals(sourceToolName) && Boolean.parseBoolean(sourceElement.getAttributeValue("enabled", "false"))) {
      toolElement.setAttribute("enabled", "true");
    }
    else if (CONSTANT_ON_RHS.equals(sourceToolName)
             && !Boolean.parseBoolean(toolElement.getAttributeValue("enabled", "false"))
             && Boolean.parseBoolean(sourceElement.getAttributeValue("enabled", "false"))) {
      toolElement.addContent(new Element("option").setAttribute("name", "myConstantShouldGoLeft").setAttribute("value", "false"));
      toolElement.setAttribute("enabled", "true");
    }
    return toolElement;
  }
}
