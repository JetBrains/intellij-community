// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.errorhandling;

import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

/**
 * @author Fabrice TIERCELIN
 */
@TestApplication
public class EmptyFinallyBlockFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new EmptyFinallyBlockInspection()));

  @Test
  public void testRemoveTry() {
    ig.get().doMemberTest(InspectionGadgetsBundle.message("remove.try.finally.block.quickfix"),
                 """
                   void m() throws Exception {
                     try { throw new Exception(); }
                     finally/**/ { }
                   }""",
                 """
                   void m() throws Exception {
                       throw new Exception();
                   }"""
    );
  }

  @Test
  public void testWithResource() {
    ig.get().doMemberTest(InspectionGadgetsBundle.message("remove.finally.block.quickfix"),
                 """
                   void m() throws Exception {
                     try (AutoCloseable r = null) { throw new Exception(); }
                     finally/**/ { }
                   }""",
                 """
                   void m() throws Exception {
                     try (AutoCloseable r = null) { throw new Exception(); }
                   }"""
    );
  }

  @Test
  public void testWithCatch() {
    ig.get().doMemberTest(InspectionGadgetsBundle.message("remove.finally.block.quickfix"),
                 """
                   void m() throws Exception {
                     try { throw new Exception(); }
                     catch (Exception e) { e.printStackTrace(); }
                     finally/**/ { }
                   }""",
                 """
                   void m() throws Exception {
                     try { throw new Exception(); }
                     catch (Exception e) { e.printStackTrace(); }
                   }"""
    );
  }

  @Test
  public void testDoNotCleanupWithFilledFinally() {
    ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("remove.finally.block.quickfix"),
                               """
                                 class X {
                                 void m() throws Exception {
                                   try { throw new Exception(); }
                                   finally/**/ { System.out.println("foo"); }
                                 }}
                                 """);
  }
}
