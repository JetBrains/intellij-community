// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ex;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;


/**
 * Merges multiple inspections settings {@link #getSourceToolNames()} into another one {@link #getMergedToolName()}
 *
 * {@see com.intellij.codeInspection.ex.InspectionElementsMergerBase} to provide more fine control over xml
 */
public abstract class InspectionElementsMerger {
  public static final ExtensionPointName<InspectionElementsMerger> EP_NAME = ExtensionPointName.create("com.intellij.inspectionElementsMerger");
  private static Map<String, InspectionElementsMerger> ourMergers;
  private static Map<String, String> ourMergedToolNames;

  @Nullable
  public static synchronized InspectionElementsMerger getMerger(String shortName) {
    initialize();
    return ourMergers.get(shortName);
  }

  public static synchronized String getMergedName(String shortName) {
    initialize();
    return ourMergedToolNames.get(shortName);
  }

  private static void initialize() {
    if (ourMergers == null) {
      ourMergers = new HashMap<>();
      ourMergedToolNames = new HashMap<>();
      for (InspectionElementsMerger merger : Extensions.getExtensions(EP_NAME)) {
        ourMergers.put(merger.getMergedToolName(), merger);
        for (String name : merger.getSourceToolNames()) {
          ourMergedToolNames.put(name, merger.getMergedToolName());
        }
      }
    }
  }

  /**
   * @return shortName of the new merged inspection.
   */
  @NotNull
  public abstract String getMergedToolName();

  /**
   * @return the shortNames of the inspections whose settings needs to be merged.
   */
  @NotNull
  public abstract String[] getSourceToolNames();

  /**
   * The ids to check for suppression.
   * If this returns an empty string array, the result of getSourceToolNames() is used instead.
   * @return the suppressIds of the merged inspections.
   */
  @NotNull
  public String[] getSuppressIds() {
    return ArrayUtilRt.EMPTY_STRING_ARRAY;
  }
}
