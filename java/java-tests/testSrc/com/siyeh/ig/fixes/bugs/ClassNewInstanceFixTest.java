// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.bugs;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.bugs.ClassNewInstanceInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

/**
 * @author Bas
 */
@TestApplication
public class ClassNewInstanceFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new ClassNewInstanceInspection()), "bugs/class_new_instance",
                        CommonQuickFixBundle.message("fix.replace.with.x", "Class.getConstructor().newInstance()"),
                        builder -> builder.addJdkVersion(LanguageLevel.JDK_1_8));

  @Test
  public void testLambda() { ig.get().doTest(); }
  @Test
  public void testCreateCatchBlocks() { ig.get().doTest(); }
  @Test
  public void testSkipCatchBlocks() { ig.get().doTest(); }
  @Test
  public void testAddThrows() { ig.get().doTest(); }
  @Test
  public void testBrokenCode() { ig.get().doTest(); }

}
