// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.restart;


import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiClass;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import org.junit.Ignore;

import java.util.function.Consumer;

@Ignore
@SkipSlowTestLocally
public class IdeRestartTest extends IsolatedIdeTestCase {
  //public void testSimpleOpenAndRestart() {
  //  doTestWithRestart(MyAction1.class, MyAction2.class);
  //}
  //
  //public static class MyAction1 implements Consumer<JavaCodeInsightTestFixture> {
  //  @Override
  //  public void accept(JavaCodeInsightTestFixture fixture) {
  //    assertNull(fixture.getJavaFacade().findClass("A"));
  //    fixture.addClass("class A { void m" + System.identityHashCode(ApplicationManager.getApplication()) + "() {}  } ");
  //    assertNotNull(fixture.findClass("A"));
  //  }
  //}
  //
  //public static class MyAction2 implements Consumer<JavaCodeInsightTestFixture> {
  //  @Override
  //  public void accept(JavaCodeInsightTestFixture fixture) {
  //    PsiClass cls = fixture.findClass("A");
  //    assertNotNull(cls);
  //    assertEmpty(cls.findMethodsByName("m" + System.identityHashCode(ApplicationManager.getApplication())));
  //  }
  //}
}
