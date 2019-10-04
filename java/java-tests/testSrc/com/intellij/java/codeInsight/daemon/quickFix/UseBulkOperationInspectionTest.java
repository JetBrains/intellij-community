// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.bulkOperation.BulkMethodInfo;
import com.intellij.codeInspection.bulkOperation.BulkMethodInfoProvider;
import com.intellij.codeInspection.bulkOperation.UseBulkOperationInspection;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testFramework.ServiceContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.stream.Stream;


public class UseBulkOperationInspectionTest extends LightQuickFixParameterizedTestCase {

  private static final BulkMethodInfoProvider TEST_PROVIDER = new BulkMethodInfoProvider() {
    @NotNull
    @Override
    public Stream<BulkMethodInfo> consumers() {
      return Stream.of(new BulkMethodInfo("testpackage.TestClass", "test", "test"));
    }
  };

  @NotNull
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    UseBulkOperationInspection inspection = new UseBulkOperationInspection();
    inspection.USE_ARRAYS_AS_LIST = true;
    return new LocalInspectionTool[]{
      inspection
    };
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    ServiceContainerUtil.registerExtension(ApplicationManager.getApplication(), BulkMethodInfoProvider.KEY, TEST_PROVIDER, getTestRootDisposable());
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/useBulkOperation";
  }
}