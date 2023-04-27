// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.refactoring.typeMigration.TypeMigrationProcessor;
import com.intellij.refactoring.typeMigration.intentions.ConvertFieldToAtomicIntention;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @see ConvertFieldToAtomicIntention
 * @author anna
 */
public class ConvertToAtomicIntentionTest extends LightQuickFixParameterizedTestCase {

  @Override
  protected String getBasePath() {
    return "/intentions/atomic";
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath() + "/java/typeMigration/testData";
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    TypeMigrationProcessor.ourSkipFailedConversionInTestMode = true;
  }

  @Override
  public void tearDown() throws Exception {
    TypeMigrationProcessor.ourSkipFailedConversionInTestMode = false;
    super.tearDown();
  }
}