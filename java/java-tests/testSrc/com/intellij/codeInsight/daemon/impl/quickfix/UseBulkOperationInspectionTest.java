// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.bulkOperation.BulkMethodInfo;
import com.intellij.codeInspection.bulkOperation.BulkMethodInfoProvider;
import com.intellij.codeInspection.bulkOperation.UseBulkOperationInspection;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.CommonClassNames;
import com.intellij.testFramework.ServiceContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.stream.Stream;


public class UseBulkOperationInspectionTest extends LightQuickFixParameterizedTestCase {

  private static final BulkMethodInfoProvider TEST_PROVIDER = new BulkMethodInfoProvider() {
    @NotNull
    @Override
    public Stream<BulkMethodInfo> consumers() {
      return Stream.of(new BulkMethodInfo("testpackage.TestClass", "test", "test", CommonClassNames.JAVA_LANG_ITERABLE));
    }
  };

  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
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