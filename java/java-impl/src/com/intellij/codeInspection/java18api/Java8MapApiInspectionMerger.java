/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInspection.java18api;

import com.intellij.codeInspection.ex.InspectionElementsMerger;
import org.jetbrains.annotations.NotNull;

public class Java8MapApiInspectionMerger extends InspectionElementsMerger {
  private static final String COLLECTION_API_INSPECTION = "Java8CollectionsApi";
  private static final String REPLACE_MAP_GET_INSPECTION = "Java8ReplaceMapGet";

  @NotNull
  @Override
  public String getMergedToolName() {
    return Java8MapApiInspection.SHORT_NAME;
  }

  @NotNull
  @Override
  public String[] getSourceToolNames() {
    return new String[] {COLLECTION_API_INSPECTION, REPLACE_MAP_GET_INSPECTION};
  }
}
