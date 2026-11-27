// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.logging;

import com.intellij.codeInspection.miscGenerics.RedundantArrayForVarargsCallInspection;
import com.intellij.java.JavaBundle;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.IGQuickFixesFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

@TestApplication
public class RedundantObjectArrayCreationInLoggingInspectionTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new RedundantArrayForVarargsCallInspection()), "logging/redundant_object_array_creation_in_logging",
                        String.format("Fix all '%s' problems in file", JavaBundle.message("inspection.redundant.array.creation.display.name")));

  @BeforeEach
  void setUp() {
    JavaCodeInsightTestFixture myFixture = ig.get().getFixture();
    myFixture.configureByFile("logging/redundant_object_array_creation_in_logging/Logger.java");
  }

  @Test
  public void testObjectArrayCreationInLoggingDebug() { ig.get().doTest(); }
  @Test
  public void testObjectArrayCreationInLoggingInfo() { ig.get().doTest(); }
  @Test
  public void testObjectArrayCreationInLoggingWarn() { ig.get().doTest(); }
  @Test
  public void testObjectArrayCreationInLoggingError() { ig.get().doTest(); }
  @Test
  public void testObjectArrayCreationInLoggingTrace() { ig.get().doTest(); }

}
