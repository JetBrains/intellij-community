// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.abstraction;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.ex.EntryPointsManagerBase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

@TestApplication
public class TypeMayBeWeakenedInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> getInspection(), getEnvironmentClasses());

  @BeforeEach
  void setUp() {
    inspectionFixture.get().getFixture().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/ig/com/siyeh/igtest/abstraction/weaken_type");
    // avoid PSI/document/model changes are not allowed during highlighting
    EntryPointsManagerBase.DEAD_CODE_EP_NAME.getExtensionList();
  }

  @Test
  public void testTypeMayBeWeakened() { inspectionFixture.get().doTest(); }
  @Test
  public void testNumberAdderDemo() { inspectionFixture.get().doTest(); }
  @Test
  public void testAutoClosableTest() { inspectionFixture.get().doTest(); }
  @Test
  public void testLambda() { inspectionFixture.get().doTest(); }
  @Test
  public void testTryWithResources() { inspectionFixture.get().doTest(); }

  private static String[] getEnvironmentClasses() {
    return new String[] {
      """
package weaken_type.sub;
public class NumberAdderImpl implements NumberAdder {
  public int doSomething() {
    return getNumberOne() + 1;
  }
  protected int getNumberOne() {
    return 1;
  }
}""",
      """
package weaken_type.sub;
public class NumberAdderExtension extends NumberAdderImpl {
  @Override
  public int getNumberOne() {
    return super.getNumberOne();
  }
}""",
      """
package java.util.function;
@FunctionalInterface
public interface Function<T, R> {
    R apply(T t);
}""",
      """
package java.util.function;
@FunctionalInterface
public interface Supplier<T> {
    T get();
}"""
    };
  }

  @NotNull
  private static InspectionProfileEntry getInspection() {
    final TypeMayBeWeakenedInspection inspection = new TypeMayBeWeakenedInspection();
    inspection.doNotWeakenToJavaLangObject = false;
    inspection.doNotWeakenReturnType = false;
    inspection.onlyWeakentoInterface = false;
    return inspection;
  }
}