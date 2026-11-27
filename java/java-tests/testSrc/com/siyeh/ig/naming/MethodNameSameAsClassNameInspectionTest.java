// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.naming;

import com.intellij.pom.java.JavaFeature;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.IGQuickFixesFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

@TestApplication
public class MethodNameSameAsClassNameInspectionTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new MethodNameSameAsClassNameInspection()), "naming/method_name_same_as_class_name",
                        "Make method constructor");

  @Test
  public void testSimple() { ig.get().doTest(); }
  @Test
  public void testModifiers() { ig.get().doTest(); }
  @Test
  public void testAbstract() { ig.get().assertQuickfixNotAvailable(); }
  @Test
  public void testInterface() { ig.get().assertQuickfixNotAvailable(); }
  @Test
  public void testConflictingCtor() { ig.get().assertQuickfixNotAvailable(); }
  @Test
  public void testConflictingCtorErasure() { ig.get().assertQuickfixNotAvailable(); }

  @Test
  @SuppressWarnings("SpellCheckingInspection")
  public void testmain() {
    JavaCodeInsightTestFixture myFixture = ig.get().getFixture();
    IdeaTestUtil.withLevel(myFixture.getModule(), JavaFeature.IMPLICIT_CLASSES.getStandardLevel(), () -> ig.get().assertQuickfixNotAvailable());
  }

}