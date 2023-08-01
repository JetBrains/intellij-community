// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.serialization;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.IGQuickFixesTestCase;

/**
 * @author Fabrice TIERCELIN
 */
public class TransientFieldInNonSerializableClassFixTest extends IGQuickFixesTestCase {
  @Override
  protected BaseInspection getInspection() {
    return new TransientFieldInNonSerializableClassInspection();
  }

  public void testRemoveTransient() {
    doMemberTest(InspectionGadgetsBundle.message("remove.modifier.quickfix", "transient"),
                 "private transient/**/ String password;\n",

                 "private String password;\n"
    );
  }

  public void testDoNotFixUsedTransient() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("remove.modifier.quickfix", "transient"),
                               """
                                 class Example implements java.io.Serializable {
                                   private transient/**/ String password;
                                 }
                                 """
    );
  }
}
