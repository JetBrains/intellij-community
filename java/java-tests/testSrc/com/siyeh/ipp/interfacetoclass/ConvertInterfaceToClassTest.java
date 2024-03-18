// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.interfacetoclass;

import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.ui.ConflictInterceptor;
import com.intellij.ui.UiInterceptors;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ConvertInterfaceToClassTest extends IPPTestCase {
  public void testBasic() { doTest(); }
  public void testExtensionMethods() { doTest(); }
  public void testInnerInterface() { doTest(); }
  public void testStaticMethods() { doTest(); }
  public void testInterfaceExtendsClass() { doTest(); }
  public void testLocalInterface() { doTest(); }

  public void testFunctionalExpressions() {
    UiInterceptors.register(new ConflictInterceptor(
      List.of("() -> {...} in Test will not compile after converting interface <b><code>FunctionalExpressions</code></b> to a class")));
    doTest();
  }

  public void testExtendsConflict() {
    UiInterceptors.register(new ConflictInterceptor(
      List.of("class <b><code>AaaImpl</code></b> implementing interface <b><code>Aaa</code></b> already extends class " +
              "<b><code>Bbb</code></b> and will not compile after converting interface <b><code>Aaa</code></b> to a class")));
    doTest();
  }

  public void testInheritorWarnings() {
    UiInterceptors.register(new ConflictInterceptor(
      List.of(
        "() -> {...} in x() in AX will not compile after converting interface <b><code>Something</code></b> to a class",
        "interface <b><code>SomethingSub</code></b> implementing interface <b><code>Something</code></b> will not compile after converting interface <b><code>Something</code></b> to a class",
        "enum <b><code>SomethingEnum</code></b> implementing interface <b><code>Something</code></b> will not compile after converting interface <b><code>Something</code></b> to a class"
      )));
    doTest();
  }

  public void testFunctionalInterface() {
    assertIntentionNotAvailable();
  }

  @Override
  protected String getRelativePath() {
    return "interfaceToClass";
  }

  @Override
  protected String getIntentionName() {
    return IntentionPowerPackBundle.message("convert.interface.to.class.intention.name");
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_15;
  }
}
