// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.ide;

import com.intellij.ide.JavaFilePasteProvider;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestApplication
public class JavaFilePasteProviderTest {
  private static final TestFixture<LightJavaProject> projectFixture =
    lightJavaProjectFixture(LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR);

  @Test
  public void testDetectClassName() {
    Project project = projectFixture.get().getProject();
    ReadAction.runBlocking(() -> {
      assertNull(JavaFilePasteProvider.detectClassName(project, "random text pasted"));
      assertEquals("X", JavaFilePasteProvider.detectClassName(project, "class X {}"));
      assertEquals("Y", JavaFilePasteProvider.detectClassName(project, "class X {} public class Y{}"));
      assertEquals("R", JavaFilePasteProvider.detectClassName(project, "record R() {}"));
      assertEquals("User", JavaFilePasteProvider.detectClassName(project, "value record User(String name) {}"));
    });
  }
}
