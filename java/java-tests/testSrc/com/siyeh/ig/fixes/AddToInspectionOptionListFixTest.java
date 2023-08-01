// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.siyeh.ig.LightJavaInspectionTestCase;
import com.siyeh.ig.bugs.ResultOfObjectAllocationIgnoredInspection;
import com.siyeh.ig.imports.StaticImportInspection;
import com.siyeh.ig.style.SizeReplaceableByIsEmptyInspection;
import com.siyeh.ig.threading.AccessToStaticFieldLockedOnInstanceInspection;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class AddToInspectionOptionListFixTest extends LightQuickFixParameterizedTestCase {

  @Override
  protected void setUp() throws Exception {
    InspectionProfileImpl.INIT_INSPECTIONS = true;
    super.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    InspectionProfileImpl.INIT_INSPECTIONS = false;
  }

  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{
      new SizeReplaceableByIsEmptyInspection(),
      new ResultOfObjectAllocationIgnoredInspection(),
      new StaticImportInspection(),
      new AccessToStaticFieldLockedOnInstanceInspection()
    };
  }

  @Override
  protected String getBasePath() {
    return "/com/siyeh/igfixes/fixes/ignore_class_fix";
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PathManagerEx.getCommunityHomePath() + LightJavaInspectionTestCase.INSPECTION_GADGETS_TEST_DATA_PATH;
  }
}