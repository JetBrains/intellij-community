// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.threading;

import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.threading.ThreadLocalSetWithNullInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

@TestApplication
public class ThreadLocalSetWithNullFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new ThreadLocalSetWithNullInspection()), "threading/thread_local_set_with_null");

  @Test
  public void testSimple() { ig.get().doTest(InspectionGadgetsBundle.message("thread.local.set.with.null.quickfix")); }

  @Test
  public void testComments() { ig.get().doTest(InspectionGadgetsBundle.message("thread.local.set.with.null.quickfix")); }

}
