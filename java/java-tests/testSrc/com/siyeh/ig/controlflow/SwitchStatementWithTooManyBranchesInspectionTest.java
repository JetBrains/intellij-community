// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.controlflow;

import com.intellij.testFramework.fixtures.JavaTestProject;
import com.intellij.testFramework.junit5.AnalysisHostTest;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.JavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.JavaTestFixtures.javaTestProjectFixture;
import static com.siyeh.ig.InspectionTestFixtures.javaInspectionFixture;

/**
 * @author Bas Leijdekkers
 */
@AnalysisHostTest
public class SwitchStatementWithTooManyBranchesInspectionTest {
  private static final TestFixture<? extends JavaTestProject> projectFixture = javaTestProjectFixture();
  private final TestFixture<? extends JavaInspectionFixture> inspectionFixture =
    javaInspectionFixture(projectFixture, () -> new SwitchStatementWithTooManyBranchesInspection());

  @Test
  public void testSimple() {
    inspectionFixture.get().doMemberTest("""
                       public void foo(int x) {
                            /*'switch' has too many branches (11)*/switch/**/ (x) {
                               case 1:
                                   break;
                               case 2:
                                   break;
                               case 3:
                                   break;
                               case 4:
                                   break;
                               case 5:
                                   break;
                               case 6:
                                   break;
                               case 7:
                                   break;
                               case 8:
                                   break;
                               case 9:
                                   break;
                               case 10:
                                   break;
                               case 11:
                                   break;
                               default:
                                   break;
                           }
                       }\
                   """);
  }

  @Test
  public void testJava12() {
    inspectionFixture.get().doMemberTest("""
                       public void foo(int x) {
                            /*'switch' has too many branches (13)*/switch/**/ (x) {
                               case 1 -> {}
                               case 2 -> {}
                               case 3 -> {}
                               case 4 -> {}
                               case 5 -> {}
                               case 6 -> {}
                               case 7 -> {}
                               case 8 -> {}
                               case 9 -> {}
                               case 10 -> {}
                               case 11,12,13 -> {}
                               default -> {}
                           }
                       }\
                   """);
  }

  @Test
  public void testJava13Expression() {
    inspectionFixture.get().doMemberTest("""
                       public int foo(int x) {
                            return /*'switch' has too many branches (13)*/switch/**/ (x) {
                               case 1 -> 0;
                               case 2 -> 0;
                               case 3 -> 0;
                               case 4 -> 0;
                               case 5 -> 0;
                               case 6 -> 0;
                               case 7 -> 0;
                               case 8 -> 0;
                               case 9 -> 0;
                               case 10 -> 0;
                               case 11,12,13 -> 0;
                               default -> 0;
                           };
                       }\
                   """);
  }

  @Test
  public void testNoWarn() {
    inspectionFixture.get().doMemberTest("""
                       public void foo(int x) {
                            switch (x) {
                               case 1:
                                   break;
                               case 2:
                                   break;
                               case 3:
                                   break;
                               case 4:
                                   break;
                               case 5:
                                   break;
                               case 6:
                                   break;
                               case 7:
                                   break;
                               case 8:
                                   break;
                               case 9:
                                   break;
                               case 10:
                                   break;
                               default:
                                   break;
                           }
                       }\
                   """);
  }
}