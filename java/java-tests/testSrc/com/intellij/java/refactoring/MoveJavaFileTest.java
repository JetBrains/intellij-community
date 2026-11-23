// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.LightMultiFileFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.refactoring.LightMultiFileFixtureKt.lightMultiFileFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class MoveJavaFileTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightMultiFileFixture> multiFile =
    lightMultiFileFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/refactoring/moveFile/");

  @Test
  public void testPackageInfo() { multiFile.get().doMoveFileTest("pack2", "pack1/package-info.java"); }
  @Test
  public void testConflict() {
    try {
      multiFile.get().doMoveFileTest("p2", "p1/B.java");
      fail("Conflict not detected!");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Package-local class <b><code>B</code></b> will no longer be accessible from field <b><code>A.b</code></b>", e.getMessage());
    }
  }
}
