// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl;

import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.io.File;

public class JsonCachedValuesTest extends BasePlatformTestCase {
  public void testGetSchemaObjectStillWorksForLocalFile() {
    File file = new File(PlatformTestUtil.getCommunityPath(), "json/backend/tests/testData/jsonSchema/schema.json");
    assertTrue(file.exists());
    VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
    assertNotNull(virtualFile);

    JsonSchemaObject schemaObject = JsonCachedValues.getSchemaObject(virtualFile, getProject());

    assertNotNull(schemaObject);
    assertEquals("http://json-schema.org/draft-04/schema", schemaObject.getId());
  }
}
