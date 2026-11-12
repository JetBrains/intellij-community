// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.interfacetoclass;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.ui.ConflictInterceptor;
import com.intellij.ui.UiInterceptors;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ipp.IPPFixtureKt.ippFixture;

@TestApplication
public class ConvertInterfaceToClassTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_15);
  private final TestFixture<IPPFixture> ipp =
    ippFixture(projectFixture, "interfaceToClass", IntentionPowerPackBundle.message("convert.interface.to.class.intention.name"));

  @Test
  public void testBasic() { ipp.get().doTest(); }
  @Test
  public void testExtensionMethods() { ipp.get().doTest(); }
  @Test
  public void testInnerInterface() { ipp.get().doTest(); }
  @Test
  public void testStaticMethods() { ipp.get().doTest(); }
  @Test
  public void testInterfaceExtendsClass() { ipp.get().doTest(); }
  @Test
  public void testLocalInterface() { ipp.get().doTest(); }

  @Test
  public void testFunctionalExpressions() {
    UiInterceptors.register(new ConflictInterceptor(
      List.of("() -> {...} in Test will not compile after converting interface <b><code>FunctionalExpressions</code></b> to a class")));
    ipp.get().doTest();
  }

  @Test
  public void testExtendsConflict() {
    UiInterceptors.register(new ConflictInterceptor(
      List.of("class <b><code>AaaImpl</code></b> implementing interface <b><code>Aaa</code></b> already extends class " +
              "<b><code>Bbb</code></b> and will not compile after converting interface <b><code>Aaa</code></b> to a class")));
    ipp.get().doTest();
  }

  @Test
  public void testInheritorWarnings() {
    UiInterceptors.register(new ConflictInterceptor(
      List.of(
        "() -> {...} in x() in AX will not compile after converting interface <b><code>Something</code></b> to a class",
        "interface <b><code>SomethingSub</code></b> implementing interface <b><code>Something</code></b> will not compile after converting interface <b><code>Something</code></b> to a class",
        "enum <b><code>SomethingEnum</code></b> implementing interface <b><code>Something</code></b> will not compile after converting interface <b><code>Something</code></b> to a class"
      )));
    ipp.get().doTest();
  }

  @Test
  public void testFunctionalInterface() {
    ipp.get().assertIntentionNotAvailable();
  }
}
