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
package com.jetbrains.jsonSchema;

import com.intellij.codeInsight.completion.CompletionTestCase;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import org.junit.Assert;

import java.util.Collections;

/**
 * @author Irina.Chernushina on 3/28/2016.
 */
public class JsonSchemaCrossReferencesTest extends CompletionTestCase {
  private final static String BASE_PATH = "/tests/testData/jsonSchema/crossReferences";

  @Override
  protected String getTestDataPath() {
    PathManagerEx.TestDataLookupStrategy strategy = PathManagerEx.guessTestDataLookupStrategy();
    if (strategy.equals(PathManagerEx.TestDataLookupStrategy.COMMUNITY)) {
      return PathManager.getHomePath() + "/json";
    }
    return PathManager.getHomePath() + "/community/json";
  }

  public void testJsonSchemaCrossReferenceCompletion() throws Exception {
    configureByFiles(null, BASE_PATH + "/completion.json", BASE_PATH + "/base.json",
                          BASE_PATH + "/inherited.json");

    String moduleDir = null;
    VirtualFile[] children = getProject().getBaseDir().getChildren();
    for (VirtualFile child : children) {
      if (child.isDirectory()) {
        moduleDir = child.getName();
        break;
      }
    }
    Assert.assertNotNull(moduleDir);

    final JsonSchemaMappingsProjectConfiguration instance = JsonSchemaMappingsProjectConfiguration.getInstance(getProject());
    final JsonSchemaMappingsConfigurationBase.SchemaInfo base =
      new JsonSchemaMappingsConfigurationBase.SchemaInfo("base", "/" + moduleDir + "/base.json", false, Collections.emptyList());
    instance.addSchema(base);

    final JsonSchemaMappingsConfigurationBase.SchemaInfo inherited
      = new JsonSchemaMappingsConfigurationBase.SchemaInfo("inherited", "/" + moduleDir + "/inherited.json", false,
                                                           Collections.singletonList(new JsonSchemaMappingsConfigurationBase.Item("*.json", true, false)));

    instance.addSchema(inherited);

    complete();
    assertStringItems("\"one\"", "\"two\"");

    LookupImpl lookup = getActiveLookup();
    if (lookup != null) lookup.hide();
    JsonSchemaService.Impl.get(getProject()).reset();
    complete();
    assertStringItems("\"one\"", "\"two\"");

    instance.removeSchema(inherited);
    instance.removeSchema(base);
  }

  public void testRefreshSchemaCompletionSimpleVariant() throws Exception {
    configureByFiles(null, BASE_PATH + "/baseCompletion.json", BASE_PATH + "/baseProperties.json");

    String moduleDir = null;
    VirtualFile moduleFile = null;
    VirtualFile[] children = getProject().getBaseDir().getChildren();
    for (VirtualFile child : children) {
      if (child.isDirectory()) {
        moduleDir = child.getName();
        moduleFile = child;
        break;
      }
    }
    Assert.assertNotNull(moduleDir);

    final JsonSchemaMappingsProjectConfiguration instance = JsonSchemaMappingsProjectConfiguration.getInstance(getProject());
    final JsonSchemaMappingsConfigurationBase.SchemaInfo base =
      new JsonSchemaMappingsConfigurationBase.SchemaInfo("base", "/" + moduleDir + "/baseProperties.json", false,
                                                         Collections.singletonList(new JsonSchemaMappingsConfigurationBase.Item("*.json", true, false)));
    instance.addSchema(base);

    testSchemaCompletion(moduleFile, "baseProperties.json");

    instance.removeSchema(base);
  }

  public void testJsonSchemaCrossReferenceCompletionWithSchemaEditing() throws Exception {
    configureByFiles(null, BASE_PATH + "/completion.json", BASE_PATH + "/base.json",
                          BASE_PATH + "/inherited.json");

    String moduleDir = null;
    VirtualFile moduleFile = null;
    VirtualFile[] children = getProject().getBaseDir().getChildren();
    for (VirtualFile child : children) {
      if (child.isDirectory()) {
        moduleDir = child.getName();
        moduleFile = child;
        break;
      }
    }
    Assert.assertNotNull(moduleDir);

    final JsonSchemaMappingsProjectConfiguration instance = JsonSchemaMappingsProjectConfiguration.getInstance(getProject());
    final JsonSchemaMappingsConfigurationBase.SchemaInfo base =
      new JsonSchemaMappingsConfigurationBase.SchemaInfo("base", "/" + moduleDir + "/base.json", false, Collections.emptyList());
    instance.addSchema(base);

    final JsonSchemaMappingsConfigurationBase.SchemaInfo inherited
      = new JsonSchemaMappingsConfigurationBase.SchemaInfo("inherited", "/" + moduleDir + "/inherited.json", false,
                                                           Collections.singletonList(new JsonSchemaMappingsConfigurationBase.Item("*.json", true, false)));

    instance.addSchema(inherited);

    testSchemaCompletion(moduleFile, "base.json");

    instance.removeSchema(inherited);
    instance.removeSchema(base);
  }

  private void testSchemaCompletion(VirtualFile moduleFile, final String fileName) {
    complete();
    assertStringItems("\"one\"", "\"two\"");

    final VirtualFile baseFile = moduleFile.findChild(fileName);
    Assert.assertNotNull(baseFile);
    FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
    Document document = fileDocumentManager.getDocument(baseFile);
    Assert.assertNotNull(document);
    String str = "\"enum\": [\"one\", \"two\"]";
    int start = document.getText().indexOf(str);
    Assert.assertTrue(start > 0);

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        document.replaceString(start, start + str.length(), "\"enum\": [\"one1\", \"two1\"]");

        fileDocumentManager.saveAllDocuments();
      }
    });
    LookupImpl lookup = getActiveLookup();
    if (lookup != null) lookup.hide();

    complete();
    assertStringItems("\"one1\"", "\"two1\"");

    lookup = getActiveLookup();
    if (lookup != null) lookup.hide();
    JsonSchemaService.Impl.get(getProject()).reset();
    complete();
    assertStringItems("\"one1\"", "\"two1\"");
  }
}
