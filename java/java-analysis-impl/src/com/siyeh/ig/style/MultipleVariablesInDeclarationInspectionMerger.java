// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.style;

import com.intellij.codeInspection.ex.InspectionElementsMergerBase;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public final class MultipleVariablesInDeclarationInspectionMerger extends InspectionElementsMergerBase {

  @NonNls private static final String MULTIPLE_DECLARATION = "MultipleDeclaration";
  @NonNls private static final String MULTIPLE_TYPED_DECLARATION = "MultipleTypedDeclaration";

  @Override
  protected boolean isEnabledByDefault(@NotNull String sourceToolName) {
    return false;
  }

  @NotNull
  @Override
  public String getMergedToolName() {
    return "MultipleVariablesInDeclaration";
  }

  @Override
  public String @NotNull [] getSourceToolNames() {
    return new String[] {
      MULTIPLE_DECLARATION,
      MULTIPLE_TYPED_DECLARATION
    };
  }

  @Override
  public String @NotNull [] getSuppressIds() {
    return new String[] {
      "VariablesOfDifferentTypesInDeclaration",
      "MultipleVariablesInDeclaration"
    };
  }

  /**
   * Add settings to merged tool when MULTIPLE_TYPED_DECLARATION is enabled and MULTIPLE_DECLARATION is disabled
   */
  @Override
  protected Element transformElement(@NotNull String sourceToolName, @NotNull Element sourceElement, @NotNull Element toolElement) {
    if (MULTIPLE_DECLARATION.equals(sourceToolName) && Boolean.parseBoolean(sourceElement.getAttributeValue("enabled", "false"))) {
      toolElement.setAttribute("enabled", "true");
    }
    else if (MULTIPLE_TYPED_DECLARATION.equals(sourceToolName)
        && !Boolean.parseBoolean(toolElement.getAttributeValue("enabled", "false"))
        && Boolean.parseBoolean(sourceElement.getAttributeValue("enabled", "false"))) {
      toolElement.addContent(new Element("option").setAttribute("name", "ignoreForLoopDeclarations").setAttribute("value", "false"));
      toolElement.addContent(new Element("option").setAttribute("name", "onlyWarnArrayDimensions").setAttribute("value", "true"));
    }
    return toolElement;
  }
}
