// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ex;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Merges multiple inspections settings {@link #getSourceToolNames()} into another one {@link #getMergedToolName()}.
 *
 * Used to preserve backward compatibility when merging several inspections into one, or replacing an inspection with a different
 * inspection. An inspection merger keeps existing {@code @SuppressWarnings} annotations working. It can also avoid the need to modify user
 * inspection profiles, because a new inspection can take one or more old inspection's settings, without the user needing to configure
 * it again.
 *
 * @see com.intellij.codeInspection.ex.InspectionElementsMergerBase to provide more fine control over XML
 */
public abstract class InspectionElementsMerger {
  public static final ExtensionPointName<InspectionElementsMerger> EP_NAME = new ExtensionPointName<>("com.intellij.inspectionElementsMerger");

  private static final ConcurrentMap<String, InspectionElementsMerger> ourAdditionalMergers = new ConcurrentHashMap<>();

  public static @Nullable InspectionElementsMerger getMerger(@NotNull String shortName) {
    InspectionElementsMerger additionalMerger = ourAdditionalMergers.get(shortName);
    if (additionalMerger == null) {
      return EP_NAME.getByKey(shortName, InspectionElementsMerger.class, InspectionElementsMerger::getMergedToolName);
    }
    return additionalMerger;
  }

  static void addMerger(@NotNull String shortName, @NotNull InspectionElementsMerger merger) {
    ourAdditionalMergers.put(shortName, merger);
  }

  /**
   * @return shortName of the new merged inspection.
   */
  @Contract(pure = true)
  public abstract @NotNull @NonNls String getMergedToolName();

  /**
   * @return the shortNames of the inspections whose settings needs to be merged.
   *
   * when one of toolNames doesn't present in the profile, default settings for that tool are expected, e.g. by default the result would be enabled with min severity WARNING
   */
  @Contract(pure = true)
  @NonNls
  public abstract String @NotNull [] getSourceToolNames();

  /**
   * The ids to check for suppression.
   * If this returns an empty string array, the result of getSourceToolNames() is used instead.
   * @return the suppressIds of the merged inspections.
   */
  @Contract(pure = true)
  @NonNls
  public String @NotNull [] getSuppressIds() {
    return ArrayUtilRt.EMPTY_STRING_ARRAY;
  }

  /**
   * @param id suppress id in code
   * @return new merged tool name
   *         null if merger is not found
   */
  public static String getMergedToolName(@NotNull String id) {
    for (InspectionElementsMerger merger : EP_NAME.getExtensionList()) {
      if (ArrayUtil.contains(id, merger.getSourceToolNames()) || ArrayUtil.contains(id, merger.getSuppressIds())) {
        return merger.getMergedToolName();
      }
    }
    return null;
  }
}
