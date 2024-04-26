// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInspection.ex.InspectionElementsMergerBase;
import com.intellij.lang.annotation.HighlightSeverity;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

public final class ExplicitArrayFillingInspectionMerger extends InspectionElementsMergerBase {

  @NotNull
  @Override
  public String getMergedToolName() {
    return "ExplicitArrayFilling";
  }

  @Override
  public String @NotNull [] getSourceToolNames() {
    return new String[]{"Java8ArraySetAll"};
  }

  @Override
  protected Element transformElement(@NotNull String sourceToolName, @NotNull Element sourceElement, @NotNull Element toolElement) {
    if (HighlightSeverity.WARNING.getName().equals(sourceElement.getAttributeValue("level")) &&
        Boolean.parseBoolean(sourceElement.getAttributeValue("enabled"))) {
      toolElement.addContent(new Element("option").setAttribute("name", "mySuggestSetAll").setAttribute("value", "true"));
    }
    return toolElement;
  }
}
