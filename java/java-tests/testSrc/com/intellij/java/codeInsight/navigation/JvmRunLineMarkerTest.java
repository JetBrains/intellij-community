// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.navigation;

import com.intellij.idea.Bombed;
import com.intellij.openapi.util.registry.Registry;

import java.util.Calendar;

public class JvmRunLineMarkerTest extends RunLineMarkerTest {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Registry.get("ide.jvm.run.marker").setValue(true, getTestRootDisposable());
  }

  @Bombed(month = Calendar.APRIL, day = 20, user = "Daniil Ovchinnikov", description = "will be resolved after IDEA-CR-30999 is merged")
  @Override
  public void testTestClassWithMain() {
    super.testTestClassWithMain();
  }
}
