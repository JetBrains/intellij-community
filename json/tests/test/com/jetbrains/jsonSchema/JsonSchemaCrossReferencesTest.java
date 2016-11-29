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
import com.intellij.openapi.extensions.AreaPicoContainer;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonBySchemaObjectReferenceContributor;
import com.jetbrains.jsonSchema.schemaFile.TestJsonSchemaMappingsProjectConfiguration;
import org.junit.Assert;

import java.util.Collections;

/**
 * @author Irina.Chernushina on 3/28/2016.
 */
public class JsonSchemaCrossReferencesTest extends CompletionTestCase {
  private final static String BASE_PATH = "/tests/testData/jsonSchema/crossReferences";
  private final static String BASE_SCHEMA_RESOLVE_PATH = "/tests/testData/jsonSchema/schemaFile/resolve";

  private FileTypeManager myFileTypeManager;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFileTypeManager = FileTypeManager.getInstance();
  }

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
      new JsonSchemaMappingsConfigurationBase.SchemaInfo("base", moduleDir + "/base.json", false, Collections.emptyList());
    instance.addSchema(base);

    final JsonSchemaMappingsConfigurationBase.SchemaInfo inherited
      = new JsonSchemaMappingsConfigurationBase.SchemaInfo("inherited", moduleDir + "/inherited.json", false,
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
      new JsonSchemaMappingsConfigurationBase.SchemaInfo("base", moduleDir + "/baseProperties.json", false,
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
      new JsonSchemaMappingsConfigurationBase.SchemaInfo("base", moduleDir + "/base.json", false, Collections.emptyList());
    instance.addSchema(base);

    final JsonSchemaMappingsConfigurationBase.SchemaInfo inherited
      = new JsonSchemaMappingsConfigurationBase.SchemaInfo("inherited", moduleDir + "/inherited.json", false,
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

    ApplicationManager.getApplication().runWriteAction(() -> {
      document.replaceString(start, start + str.length(), "\"enum\": [\"one1\", \"two1\"]");

      fileDocumentManager.saveAllDocuments();
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

  public void testJsonSchemaRefsCrossResolve() throws Exception {
    configureByFiles(null, BASE_SCHEMA_RESOLVE_PATH + "/referencingSchema.json", BASE_SCHEMA_RESOLVE_PATH + "/localRefSchema.json");

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

    AreaPicoContainer container = Extensions.getArea(getProject()).getPicoContainer();
    final String key = JsonSchemaMappingsProjectConfiguration.class.getName();
    container.unregisterComponent(key);
    container.registerComponentImplementation(key, TestJsonSchemaMappingsProjectConfiguration.class);

    final JsonSchemaMappingsProjectConfiguration instance = JsonSchemaMappingsProjectConfiguration.getInstance(getProject());
    final JsonSchemaMappingsConfigurationBase.SchemaInfo base =
      new JsonSchemaMappingsConfigurationBase.SchemaInfo("base", moduleDir + "/localRefSchema.json", false, Collections.emptyList());
    instance.addSchema(base);

    final JsonSchemaMappingsConfigurationBase.SchemaInfo inherited
      = new JsonSchemaMappingsConfigurationBase.SchemaInfo("inherited", moduleDir + "/referencingSchema.json", false, Collections.emptyList());

    instance.addSchema(inherited);

    try {
      ApplicationManager.getApplication().runWriteAction(() -> myFileTypeManager.associatePattern(JsonSchemaFileType.INSTANCE, "*Schema.json"));
      JsonSchemaService.Impl.get(getProject()).reset();

      testIsSchemaFile(moduleFile, "localRefSchema.json");
      testIsSchemaFile(moduleFile, "referencingSchema.json");

      int offset = myEditor.getCaretModel().getPrimaryCaret().getOffset();
      final PsiReference referenceAt = myFile.findReferenceAt(offset);
      Assert.assertNotNull(referenceAt);
      final PsiElement resolve = referenceAt.resolve();
      Assert.assertNotNull(resolve);
      Assert.assertEquals("\"baseEnum\"", resolve.getText());

      ApplicationManager.getApplication().runWriteAction(() -> myFileTypeManager.removeAssociatedExtension(JsonSchemaFileType.INSTANCE, "*Schema.json"));
    } finally {
      container.unregisterComponent(key);
      container.registerComponentImplementation(key, JsonSchemaMappingsProjectConfiguration.class);
    }

    instance.removeSchema(inherited);
    instance.removeSchema(base);
  }

  private void testIsSchemaFile(VirtualFile moduleFile, String name) {
    final VirtualFile child = moduleFile.findChild(name);
    Assert.assertNotNull(child);
    Assert.assertTrue(JsonSchemaFileType.INSTANCE.equals(child.getFileType()));
    Assert.assertTrue(JsonSchemaMappingsProjectConfiguration.getInstance(getProject()).isRegisteredSchemaFile(child));
  }

  public void testJsonSchemaGlobalRefsCrossResolve() throws Exception {
    configureByFiles(null, BASE_SCHEMA_RESOLVE_PATH + "/referencingGlobalSchema.json");

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

    AreaPicoContainer container = Extensions.getArea(getProject()).getPicoContainer();
    final String key = JsonSchemaMappingsProjectConfiguration.class.getName();
    container.unregisterComponent(key);
    container.registerComponentImplementation(key, TestJsonSchemaMappingsProjectConfiguration.class);

    final JsonSchemaMappingsProjectConfiguration instance = JsonSchemaMappingsProjectConfiguration.getInstance(getProject());
    final JsonSchemaMappingsConfigurationBase.SchemaInfo inherited
      = new JsonSchemaMappingsConfigurationBase.SchemaInfo("inherited", moduleDir + "/referencingGlobalSchema.json", false, Collections.emptyList());

    instance.addSchema(inherited);

    try {
      ApplicationManager.getApplication().runWriteAction(() -> myFileTypeManager.associatePattern(JsonSchemaFileType.INSTANCE, "*Schema.json"));
      int offset = myEditor.getCaretModel().getPrimaryCaret().getOffset();
      final PsiReference referenceAt = myFile.findReferenceAt(offset);
      Assert.assertNotNull(referenceAt);
      final PsiElement resolve = referenceAt.resolve();
      Assert.assertNotNull(resolve);
      Assert.assertEquals("\"enum\"", resolve.getText());

      ApplicationManager.getApplication().runWriteAction(() -> myFileTypeManager.removeAssociatedExtension(JsonSchemaFileType.INSTANCE, "*Schema.json"));
    } finally {
      container.unregisterComponent(key);
      container.registerComponentImplementation(key, JsonSchemaMappingsProjectConfiguration.class);
    }

    instance.removeSchema(inherited);
  }

  public void testJson2SchemaPropertyResolve() throws Exception {
    configureByFiles(null, BASE_PATH + "/testFileForBaseProperties.json", BASE_PATH + "/baseProperties.json");

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
    final JsonSchemaMappingsConfigurationBase.SchemaInfo inherited
      = new JsonSchemaMappingsConfigurationBase.SchemaInfo("inherited", moduleDir + "/baseProperties.json", false,
                                                           Collections.singletonList(
                                                             new JsonSchemaMappingsConfigurationBase.Item("*.json", true, false)));

    instance.addSchema(inherited);
    JsonSchemaService.Impl.get(getProject()).reset();

    ApplicationManager.getApplication().runWriteAction(() -> myFileTypeManager.associatePattern(JsonSchemaFileType.INSTANCE, "*Schema.json"));
    try {

      int offset = myEditor.getCaretModel().getPrimaryCaret().getOffset();
      PsiElement element = myFile.findElementAt(offset);
      boolean found = false;
      while (element.getTextRange().contains(offset)) {
        if (JsonBySchemaObjectReferenceContributor.REF_PATTERN.accepts(element)) {
          found = true;
          break;
        }
        element = element.getParent();
      }
      Assert.assertTrue(found);
      final PsiReference referenceAt = myFile.findReferenceAt(offset);
      Assert.assertNotNull(referenceAt);
      final PsiElement resolve = referenceAt.resolve();
      Assert.assertNotNull(resolve);
      Assert.assertEquals("\"baseEnum\"", resolve.getText());
      Assert.assertEquals("baseProperties.json", resolve.getContainingFile().getName());

    } finally {
      instance.removeSchema(inherited);
      ApplicationManager.getApplication().runWriteAction(() -> myFileTypeManager.removeAssociatedExtension(JsonSchemaFileType.INSTANCE, "*Schema.json"));
    }
  }
}
