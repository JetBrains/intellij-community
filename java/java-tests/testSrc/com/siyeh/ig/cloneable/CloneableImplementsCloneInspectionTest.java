// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.cloneable;

import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
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
public class CloneableImplementsCloneInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new CloneableImplementsCloneInspection());

  @Test
  public void testSimple() {
    inspectionFixture.get().doTest("class /*'X' is 'Cloneable' but does not define 'clone()' method*//*_*/X/**/ implements Cloneable {}" +
           "class Y extends X {}" +
           "class Z implements Cloneable {" +
           "  Z copy() {" +
           "    try {" +
           "      return (Z)clone();" +
           "    } catch (CloneNotSupportedException ignore) {" +
           "      return null;" +
           "    }" +
           "  }" +
           "}");
    inspectionFixture.get().checkQuickFix(InspectionGadgetsBundle.message("remove.cloneable.quickfix"),
                  "class X {}" +
                  "class Y extends X {}" +
                  "class Z implements Cloneable {" +
                  "  Z copy() {" +
                  "    try {" +
                  "      return (Z)clone();" +
                  "    } catch (CloneNotSupportedException ignore) {" +
                  "      return null;" +
                  "    }" +
                  "  }" +
                  "}");
  }

  @Test
  public void testInheritedClone() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    final CloneableImplementsCloneInspection inspection = new CloneableImplementsCloneInspection();
    inspection.m_ignoreCloneableDueToInheritance = false;
    myFixture.enableInspections(inspection);
    inspectionFixture.get().doTest("class /*'X' is 'Cloneable' but does not define 'clone()' method*/X/**/ implements Cloneable {}" +
           "class /*'Y' is 'Cloneable' but does not define 'clone()' method*/Y/**/ extends X {}" +
           "class Z implements Cloneable {" +
           "  Z copy() {" +
           "    try {" +
           "      return (Z)clone();" +
           "    } catch (CloneNotSupportedException ignore) {" +
           "      return null;" +
           "    }" +
           "  }" +
           "}");
  }

  @Test
  public void testCloneCalled() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    final CloneableImplementsCloneInspection inspection = new CloneableImplementsCloneInspection();
    inspection.ignoreWhenCloneCalled = false;
    myFixture.enableInspections(inspection);
    inspectionFixture.get().doTest("class /*'X' is 'Cloneable' but does not define 'clone()' method*/X/**/ implements Cloneable {}" +
           "class Y extends X {}" +
           "class /*'Z' is 'Cloneable' but does not define 'clone()' method*/Z/**/ implements Cloneable {" +
           "  Z copy() {" +
           "    try {" +
           "      return (Z)clone();" +
           "    } catch (CloneNotSupportedException ignore) {" +
           "      return null;" +
           "    }" +
           "  }" +
           "}");
  }
}