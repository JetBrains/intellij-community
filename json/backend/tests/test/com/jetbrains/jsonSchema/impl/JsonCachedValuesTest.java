// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.io.File;

import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class JsonCachedValuesTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  @Test
  public void testGetSchemaObjectStillWorksForLocalFile() {
    File file = new File(PlatformTestUtil.getCommunityPath(), "json/backend/tests/testData/jsonSchema/schema.json");
    assertTrue(file.exists());
    VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
    assertNotNull(virtualFile);

    JsonSchemaObject schemaObject = ReadAction.computeBlocking(() -> JsonCachedValues.getSchemaObject(virtualFile, projectFixture.get()));

    assertNotNull(schemaObject);
    assertEquals("http://json-schema.org/draft-04/schema", schemaObject.getId());
  }
}
