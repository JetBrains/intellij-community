// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.cloneable;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class CloneableImplementsCloneInspectionTest extends LightJavaInspectionTestCase {

  public void testSimple() {
    doTest("class /*'X' is 'Cloneable' but does not define 'clone()' method*//*_*/X/**/ implements Cloneable {}" +
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
    checkQuickFix(InspectionGadgetsBundle.message("remove.cloneable.quickfix"),
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

  public void testInheritedClone() {
    final CloneableImplementsCloneInspection inspection = new CloneableImplementsCloneInspection();
    inspection.m_ignoreCloneableDueToInheritance = false;
    myFixture.enableInspections(inspection);
    doTest("class /*'X' is 'Cloneable' but does not define 'clone()' method*/X/**/ implements Cloneable {}" +
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

  public void testCloneCalled() {
    final CloneableImplementsCloneInspection inspection = new CloneableImplementsCloneInspection();
    inspection.ignoreWhenCloneCalled = false;
    myFixture.enableInspections(inspection);
    doTest("class /*'X' is 'Cloneable' but does not define 'clone()' method*/X/**/ implements Cloneable {}" +
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

  @Override
  protected @Nullable InspectionProfileEntry getInspection() {
    return new CloneableImplementsCloneInspection();
  }
}