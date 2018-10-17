// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.structureView;

import com.intellij.ide.util.InheritedMembersNodeProvider;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

public class RecursiveTest extends LightCodeInsightFixtureTestCase {
  public void test() {
    myFixture.configureByText("I.java", "interface I {" +
                                        "  class Impl implements I {" +
                                        "  }" +
                                        "};");
    myFixture.testStructureView(component -> {
      component.setActionActive(InheritedMembersNodeProvider.ID, true);
      PlatformTestUtil.assertTreeEqual(component.getTree(),
                                       "-I.java\n" +
                                       " -I\n" +
                                       "  +Impl");
    });
  }
}
