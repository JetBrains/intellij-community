// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi;

import com.intellij.core.CoreJavaCodeStyleManager;
import com.intellij.core.CorePackageIndex;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaVersionService;
import com.intellij.openapi.roots.PackageIndex;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * The basic Java plugin registers an open default for each Java PSI service that the Java backend implements.
 * With the Java plugin loaded, each override must win over its default.
 */
@TestApplication
public class JavaPluginOverridesBasicDefaultsTest {
  private static final TestFixture<Project> projectFixture = projectFixture();

  @Test
  public void applicationServices() {
    assertNotEquals(JavaVersionService.class, JavaVersionService.getInstance().getClass());
  }

  @Test
  public void projectServices() {
    Project project = projectFixture.get();
    assertNotEquals(CoreJavaCodeStyleManager.class, JavaCodeStyleManager.getInstance(project).getClass());
    assertNotEquals(CorePackageIndex.class, PackageIndex.getInstance(project).getClass());
  }
}
