/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.jsonSchema.schemaFile;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.extensions.AreaPicoContainer;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import com.jetbrains.jsonSchema.JsonSchemaFileType;
import com.jetbrains.jsonSchema.JsonSchemaMappingsProjectConfiguration;
import org.junit.Assert;

/**
 * @author Irina.Chernushina on 4/1/2016.
 */
public class JsonSchemaFileResolveTest extends LightPlatformCodeInsightFixtureTestCase {
  private final static String BASE_PATH = "/tests/testData/jsonSchema/schemaFile/resolve";
  private FileTypeManager myFileTypeManager;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFileTypeManager = FileTypeManagerEx.getInstanceEx();
  }

  @Override
  protected String getTestDataPath() {
    PathManagerEx.TestDataLookupStrategy strategy = PathManagerEx.guessTestDataLookupStrategy();
    if (strategy.equals(PathManagerEx.TestDataLookupStrategy.COMMUNITY)) {
      return PathManager.getHomePath() + "/json" + BASE_PATH;
    }
    return PathManager.getHomePath() + "/community/json" + BASE_PATH;
  }

  public void testResolveLocalRef() throws Exception {
    AreaPicoContainer container = Extensions.getArea(getProject()).getPicoContainer();
    final String key = JsonSchemaMappingsProjectConfiguration.class.getName();
    container.unregisterComponent(key);
    container.registerComponentImplementation(key, TestJsonSchemaMappingsProjectConfiguration.class);

    try {
      WriteCommandAction.runWriteCommandAction(getProject(), () -> myFileTypeManager.associatePattern(JsonSchemaFileType.INSTANCE, "*Schema.json"));
      PsiReference position = myFixture.getReferenceAtCaretPosition("localRefSchema.json");
      Assert.assertNotNull(position);
      PsiElement resolve = position.resolve();
      Assert.assertNotNull(resolve);
      Assert.assertEquals("\"baseEnum\"", resolve.getText());

      WriteCommandAction.runWriteCommandAction(getProject(), () -> myFileTypeManager.removeAssociatedExtension(JsonSchemaFileType.INSTANCE, "*Schema.json"));
    } finally {
      container.unregisterComponent(key);
      container.registerComponentImplementation(key, JsonSchemaMappingsProjectConfiguration.class);
    }
  }

  public void testResolveExternalRef() throws Exception {
    AreaPicoContainer container = Extensions.getArea(getProject()).getPicoContainer();
    final String key = JsonSchemaMappingsProjectConfiguration.class.getName();
    container.unregisterComponent(key);
    container.registerComponentImplementation(key, TestJsonSchemaMappingsProjectConfiguration.class);

    try {
      WriteCommandAction.runWriteCommandAction(getProject(), () -> myFileTypeManager.associatePattern(JsonSchemaFileType.INSTANCE, "*Schema.json"));
      PsiReference position = myFixture.getReferenceAtCaretPosition("localRefSchema.json");
      Assert.assertNotNull(position);
      PsiElement resolve = position.resolve();
      Assert.assertNotNull(resolve);
      Assert.assertEquals("\"baseEnum\"", resolve.getText());

      WriteCommandAction.runWriteCommandAction(getProject(), () -> myFileTypeManager.removeAssociatedExtension(JsonSchemaFileType.INSTANCE, "*Schema.json"));
    } finally {
      container.unregisterComponent(key);
      container.registerComponentImplementation(key, JsonSchemaMappingsProjectConfiguration.class);
    }
  }
}
