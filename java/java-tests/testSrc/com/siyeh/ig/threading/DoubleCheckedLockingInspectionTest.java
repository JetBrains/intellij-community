// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.threading;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class DoubleCheckedLockingInspectionTest extends LightJavaInspectionTestCase {

  public void testDoubleCheckedLocking() {
    doTest();
    assertQuickFixNotAvailable(InspectionGadgetsBundle.message("introduce.holder.class.quickfix"));
  }

  public void testQuickFix() {
    doTest();
    checkQuickFix(InspectionGadgetsBundle.message("introduce.holder.class.quickfix"));
  }

  public void testQuickFixNotAvailable() {
    doTest();
    assertQuickFixNotAvailable(InspectionGadgetsBundle.message("introduce.holder.class.quickfix"));
  }

  @SuppressWarnings("DoubleCheckedLocking")
  public void testSimple() {
    doTest("""
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

  public void testVolatile() {
    doTest("""
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

  public void testVolatile2() {
    doTest("""
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

  @Override
  protected InspectionProfileEntry getInspection() {
    return new DoubleCheckedLockingInspection();
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_11;
  }
}
