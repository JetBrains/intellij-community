// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.threading;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class DoubleCheckedLockingInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_11);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new DoubleCheckedLockingInspection());

  @Test
  public void testDoubleCheckedLocking() {
    inspectionFixture.get().doTest();
    inspectionFixture.get().assertQuickFixNotAvailable(InspectionGadgetsBundle.message("introduce.holder.class.quickfix"));
  }

  @Test
  public void testQuickFix() {
    inspectionFixture.get().doTest();
    inspectionFixture.get().checkQuickFix(InspectionGadgetsBundle.message("introduce.holder.class.quickfix"));
  }

  @Test
  public void testQuickFixNotAvailable() {
    inspectionFixture.get().doTest();
    inspectionFixture.get().assertQuickFixNotAvailable(InspectionGadgetsBundle.message("introduce.holder.class.quickfix"));
  }

  @Test
  @SuppressWarnings("DoubleCheckedLocking")
  public void testSimple() {
    inspectionFixture.get().doTest("""
             class A {    private  boolean initialized;
                 private void initialize() {
                     /*Double-checked locking*/if/**/ (initialized == false) {
                         synchronized (this) {
                             if (initialized == false) {
                                 initialized = true;
                             }
                         }
                     }
                 }
             }""");
  }

  @Test
  public void testVolatile() {
    inspectionFixture.get().doTest("""
             class X {    private volatile boolean initialized;
                 private void initialize() {
                     if (initialized == false) {
                         synchronized (this) {
                             if (initialized == false) {
                                 initialized = true;
                             }
                         }
                     }
                 }
             }""");
  }

  @Test
  public void testVolatile2() {
    inspectionFixture.get().doTest("""
             class Main654 {
               private volatile int myListenPort = -1;
               private void ensureListening() {
                 if (myListenPort < 0) {
                   synchronized (this) {
                     if (myListenPort < 0) {
                       myListenPort = startListening();
                     }
                   }
                 }
               }
               private int startListening() {
                 return 0;
               }
             }""");
  }
}
