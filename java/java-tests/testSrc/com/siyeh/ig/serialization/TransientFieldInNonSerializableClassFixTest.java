// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.serialization;

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
public class TransientFieldInNonSerializableClassFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new TransientFieldInNonSerializableClassInspection()));

  @Test
  public void testRemoveTransient() {
    ig.get().doMemberTest(InspectionGadgetsBundle.message("remove.modifier.quickfix", "transient"),
                 "private transient/**/ String password;\n",

                 "private String password;\n"
    );
  }

  @Test
  public void testDoNotFixUsedTransient() {
    ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("remove.modifier.quickfix", "transient"),
                               """
                                 class Example implements java.io.Serializable {
                                   private transient/**/ String password;
                                 }
                                 """
    );
  }
}
