/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.codeInspection.unusedImport;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.BaseJavaBatchLocalInspectionTool;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.PairedUnfairLocalInspectionTool;
import com.siyeh.ig.imports.UnusedImportInspection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * User: anna
 * Date: 17-Feb-2006
 */
public class UnusedImportLocalInspection extends BaseJavaBatchLocalInspectionTool implements PairedUnfairLocalInspectionTool {
  @NonNls public static final String SHORT_NAME = "UNUSED_IMPORT";
  public static final String DISPLAY_NAME = InspectionsBundle.message("unused.import");

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.IMPORTS_GROUP_NAME;
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  @Override
  @NotNull
  @NonNls
  public String getShortName() {
    return SHORT_NAME;
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  @Override
  public String getInspectionForBatchShortName() {
    return new UnusedImportInspection().getShortName();
  }
}
