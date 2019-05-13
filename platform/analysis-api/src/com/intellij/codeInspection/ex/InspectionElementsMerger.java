// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ex;

import com.intellij.openapi.extensions.ExtensionPointName;
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

  @Nullable
  public static synchronized InspectionElementsMerger getMerger(@NotNull String shortName) {
    if (ourMergers == null) {
      ourMergers = new HashMap<>();
      for (InspectionElementsMerger merger : EP_NAME.getExtensionList()) {
        ourMergers.put(merger.getMergedToolName(), merger);
      }
    }
    return ourMergers.get(shortName);
  }

  /**
   * @return shortName of the new merged inspection.
   */
  @NotNull
  public abstract String getMergedToolName();

  /**
   * @return the shortNames of the inspections whose settings needs to be merged.
   * 
   * when one of toolNames doesn't present in the profile, default settings for that tool are expected, e.g. by default the result would be enabled with min severity WARNING
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

  /**
   * @param id suppress id in code
   * @return new merged tool name
   *         null if merger is not found
   */
  public static String getMergedToolName(@NotNull String id) {
    for (InspectionElementsMerger merger : EP_NAME.getExtensionList()) {
      for (String sourceToolName : merger.getSourceToolNames()) {
        if (id.equals(sourceToolName)) {
          return merger.getMergedToolName();
        }
      }
      for (String suppressId : merger.getSuppressIds()) {
        if (id.equals(suppressId)) {
          return merger.getMergedToolName();
        }
      }
    }
    return null;
  }
}
