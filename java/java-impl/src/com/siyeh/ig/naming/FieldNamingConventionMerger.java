// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package com.siyeh.ig.naming;

import com.intellij.codeInspection.naming.AbstractNamingConventionMerger;
import com.intellij.psi.PsiField;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public final class FieldNamingConventionMerger extends AbstractNamingConventionMerger<PsiField> {
  public FieldNamingConventionMerger() {
    super(new FieldNamingConventionInspection());
  }

  @Override
  protected Element getSourceElement(@NotNull Map<String, Element> inspectionElements, @NotNull String sourceToolName) {
    if (sourceToolName.equals(ConstantWithMutableFieldTypeNamingConvention.CONSTANT_WITH_MUTABLE_FIELD_TYPE_NAMING_CONVENTION_SHORT_NAME)) {
      Element element = inspectionElements.get(ConstantNamingConvention.CONSTANT_NAMING_CONVENTION_SHORT_NAME);
      if (element != null) {
        for (@NonNls Element option : element.getChildren("option")) {
          if ("onlyCheckImmutables".equals(option.getAttributeValue("name"))) {
            if (!Boolean.parseBoolean(option.getAttributeValue("value"))) {
              return element;
            }
            break;
          }
        }
      }

      element = inspectionElements.get(StaticVariableNamingConvention.STATIC_VARIABLE_NAMING_CONVENTION_SHORT_NAME);
      if (element != null) {
        for (@NonNls Element option : element.getChildren("option")) {
          if ("checkMutableFinals".equals(option.getAttributeValue("name"))) {
            return Boolean.parseBoolean(option.getAttributeValue("value")) ? element : null;
          }
        }
      }
      return null;
    }

    return super.getSourceElement(inspectionElements, sourceToolName);
  }
}
