/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection.ex;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.util.ArrayUtilRt;
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
  public synchronized static InspectionElementsMerger getMerger(String shortName) {
    if (ourMergers == null) {
      ourMergers = new HashMap<>();
      for (InspectionElementsMerger merger : Extensions.getExtensions(EP_NAME)) {
        ourMergers.put(merger.getMergedToolName(), merger);
      }
    }
    return ourMergers.get(shortName);
  }

  public abstract String getMergedToolName();

  /**
   * @return the shortNames of the merged inspections
   */
  public abstract String[] getSourceToolNames();

  /**
   * The ids to check for suppression.
   * If this returns an empty string array, the result of getSourceToolNames() is used instead.
   * @return the suppressIds of the merged inspections.
   */
  public String[] getSuppressIds() {
    return ArrayUtilRt.EMPTY_STRING_ARRAY;
  }
}
