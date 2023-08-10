// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.redundancy;

import com.intellij.codeInspection.ex.InspectionElementsMergerBase;
import com.intellij.util.ArrayUtilRt;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;

final class RedundantStringOperationMerger extends InspectionElementsMergerBase {
  private static final String OLD_MERGER_NAME = "RedundantStringOperation";
  private static final Set<String> OLD_SOURCE_NAMES = Set.of("StringToString", "SubstringZero", "ConstantStringIntern");

  @NotNull
  @Override
  public String getMergedToolName() {
    return "StringOperationCanBeSimplified";
  }

  @Override
  protected Element getSourceElement(@NotNull Map<String, Element> inspectionElements, @NotNull String sourceToolName) {
    if (inspectionElements.containsKey(sourceToolName)) {
      return inspectionElements.get(sourceToolName);
    }

    if (sourceToolName.equals(OLD_MERGER_NAME)) {//need to merge initial tools to get merged redundant string operations
      return new InspectionElementsMergerBase(){
        @NotNull
        @Override
        public String getMergedToolName() {
          return OLD_MERGER_NAME;
        }

        @Override
        public String @NotNull [] getSourceToolNames() {
          return OLD_SOURCE_NAMES.toArray(ArrayUtilRt.EMPTY_STRING_ARRAY);
        }

        @Override
        public Element merge(@NotNull Map<String, Element> inspectionElements) {
          return super.merge(inspectionElements);
        }

        @Override
        protected boolean writeMergedContent(@NotNull Element toolElement) {
          return true;
        }
      }.merge(inspectionElements);
    }
    else if (OLD_SOURCE_NAMES.contains(sourceToolName)) {
      Element merged = inspectionElements.get(OLD_MERGER_NAME);
      if (merged != null) { // RedundantStringOperation already replaced the content
        Element clone = merged.clone();
        clone.setAttribute("class", sourceToolName);
        return clone;
      }
    }
    return null;
  }

  @Override
  public String @NotNull [] getSourceToolNames() {
    return new String[] {
      "StringToString",
      "SubstringZero",
      "ConstantStringIntern",
      "StringConstructor",
      OLD_MERGER_NAME
    };
  }

  @Override
  public String @NotNull [] getSuppressIds() {
    return new String[] {
      "StringToString", "RedundantStringToString",
      "SubstringZero", "ConstantStringIntern",
      "RedundantStringConstructorCall", "StringConstructor", OLD_MERGER_NAME
    };
  }
}
