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

import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.AreaPicoContainer;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonBySchemaObjectReferenceContributor;
import com.jetbrains.jsonSchema.schemaFile.TestJsonSchemaMappingsProjectConfiguration;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.util.Collections;

/**
 * @author Irina.Chernushina on 3/28/2016.
 */
public class JsonSchemaCrossReferencesTest extends JsonSchemaHeavyAbstractTest {
  private final static String BASE_PATH = "/tests/testData/jsonSchema/crossReferences";
  private final static String BASE_SCHEMA_RESOLVE_PATH = "/tests/testData/jsonSchema/schemaFile/resolve";

  @Override
  protected String getBasePath() {
    return BASE_PATH;
  }

  public void testJsonSchemaCrossReferenceCompletion() throws Exception {
    skeleton(new Callback() {
      @Override
      public void doCheck() {
        assertStringItems("\"one\"", "\"two\"");

        LookupImpl lookup = getActiveLookup();
        if (lookup != null) lookup.hide();
        JsonSchemaService.Impl.get(getProject()).reset();
        doHighlighting();
        complete();
        assertStringItems("\"one\"", "\"two\"");
      }

      @Override
      public void configureFiles() throws Exception {
        configureByFiles(null, "/completion.json", "/baseSchema.json", "/inheritedSchema.json");
      }

      @Override
      public void registerSchemes() {
        final String moduleDir = getModuleDir(getProject());

        final JsonSchemaMappingsConfigurationBase.SchemaInfo base =
          new JsonSchemaMappingsConfigurationBase.SchemaInfo("base", moduleDir + "/baseSchema.json", false, Collections.emptyList());
        addSchema(base);

        final JsonSchemaMappingsConfigurationBase.SchemaInfo inherited
          = new JsonSchemaMappingsConfigurationBase.SchemaInfo("inherited", moduleDir + "/inheritedSchema.json", false,
                                                               Collections.singletonList(new JsonSchemaMappingsConfigurationBase.Item("*.json", true, false)));

        addSchema(inherited);
      }
    });
  }

  public void testRefreshSchemaCompletionSimpleVariant() throws Exception {
    skeleton(new Callback() {
      private String myModuleDir;

      @Override
      public void registerSchemes() {
        myModuleDir = getModuleDir(getProject());

        final JsonSchemaMappingsConfigurationBase.SchemaInfo base =
          new JsonSchemaMappingsConfigurationBase.SchemaInfo("base", myModuleDir + "/basePropertiesSchema.json", false,
                                                             Collections.singletonList(new JsonSchemaMappingsConfigurationBase.Item("*.json", true, false)));
        addSchema(base);
      }

      @Override
      public void configureFiles() throws Exception {
        configureByFiles(null, "/baseCompletion.json", "/basePropertiesSchema.json");
      }

      @Override
      public void doCheck() {
        final VirtualFile moduleFile = getProject().getBaseDir().findChild(myModuleDir);
        assertNotNull(moduleFile);
        checkSchemaCompletion(moduleFile, "basePropertiesSchema.json");
      }
    });
  }

  public void testJsonSchemaCrossReferenceCompletionWithSchemaEditing() throws Exception {
    skeleton(new Callback() {
      private String myModuleDir;

      @Override
      public void registerSchemes() {
        myModuleDir = getModuleDir(getProject());

        final JsonSchemaMappingsConfigurationBase.SchemaInfo base =
          new JsonSchemaMappingsConfigurationBase.SchemaInfo("base", myModuleDir + "/baseSchema.json", false, Collections.emptyList());
        addSchema(base);

        final JsonSchemaMappingsConfigurationBase.SchemaInfo inherited
          = new JsonSchemaMappingsConfigurationBase.SchemaInfo("inherited", myModuleDir + "/inheritedSchema.json", false,
                                                               Collections.singletonList(new JsonSchemaMappingsConfigurationBase.Item("*.json", true, false)));

        addSchema(inherited);
      }

      @Override
      public void configureFiles() throws Exception {
        configureByFiles(null, "/completion.json", "/baseSchema.json", "/inheritedSchema.json");
      }

      @Override
      public void doCheck() {
        final VirtualFile moduleFile = getProject().getBaseDir().findChild(myModuleDir);
        assertNotNull(moduleFile);
        checkSchemaCompletion(moduleFile, "baseSchema.json");
      }
    });
  }

  private void checkSchemaCompletion(VirtualFile moduleFile, final String fileName) {
    doHighlighting();
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

    doHighlighting();
    complete();
    assertStringItems("\"one1\"", "\"two1\"");

    lookup = getActiveLookup();
    if (lookup != null) lookup.hide();
    JsonSchemaService.Impl.get(getProject()).reset();
    doHighlighting();
    complete();
    assertStringItems("\"one1\"", "\"two1\"");
  }

  public void testJsonSchemaRefsCrossResolve() throws Exception {
    skeleton(new Callback() {
      @Override
      public void doCheck() {
        int offset = myEditor.getCaretModel().getPrimaryCaret().getOffset();
        final PsiReference referenceAt = myFile.findReferenceAt(offset);
        Assert.assertNotNull(referenceAt);
        final PsiElement resolve = referenceAt.resolve();
        Assert.assertNotNull(resolve);
        Assert.assertEquals("\"baseEnum\"", resolve.getText());
      }

      @Override
      public void configureFiles() throws Exception {
        configureByFiles(null, "/referencingSchema.json", "/localRefSchema.json");
      }

      @Override
      public void registerSchemes() {
        final String moduleDir = getModuleDir(getProject());

        final JsonSchemaMappingsConfigurationBase.SchemaInfo base =
          new JsonSchemaMappingsConfigurationBase.SchemaInfo("base", moduleDir + "/localRefSchema.json", false, Collections.emptyList());
        addSchema(base);

        final JsonSchemaMappingsConfigurationBase.SchemaInfo inherited
          = new JsonSchemaMappingsConfigurationBase.SchemaInfo("inherited", moduleDir + "/referencingSchema.json", false, Collections.emptyList());

        addSchema(inherited);
      }
    });
  }

  public void testJsonSchemaGlobalRefsCrossResolve() throws Exception {
    skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        final String moduleDir = getModuleDir(getProject());

        AreaPicoContainer container = Extensions.getArea(getProject()).getPicoContainer();
        final String key = JsonSchemaMappingsProjectConfiguration.class.getName();
        container.unregisterComponent(key);
        container.registerComponentImplementation(key, TestJsonSchemaMappingsProjectConfiguration.class);

        final JsonSchemaMappingsConfigurationBase.SchemaInfo inherited
          = new JsonSchemaMappingsConfigurationBase.SchemaInfo("inherited", moduleDir + "/referencingGlobalSchema.json", false, Collections.emptyList());

        addSchema(inherited);
      }

      @Override
      public void configureFiles() throws Exception {
        configureByFiles(null, "/referencingGlobalSchema.json");
      }

      @Override
      public void doCheck() {
        int offset = myEditor.getCaretModel().getPrimaryCaret().getOffset();
        final PsiReference referenceAt = myFile.findReferenceAt(offset);
        Assert.assertNotNull(referenceAt);
        final PsiElement resolve = referenceAt.resolve();
        Assert.assertNotNull(resolve);
        Assert.assertEquals("\"enum\"", resolve.getText());
      }
    });
  }

  public void testJson2SchemaPropertyResolve() throws Exception {
    skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        final String moduleDir = getModuleDir(getProject());
        final JsonSchemaMappingsConfigurationBase.SchemaInfo inherited
          = new JsonSchemaMappingsConfigurationBase.SchemaInfo("inherited", moduleDir + "/basePropertiesSchema.json", false,
                                                               Collections.singletonList(
                                                                 new JsonSchemaMappingsConfigurationBase.Item("*.json", true, false)));

        addSchema(inherited);
      }

      @Override
      public void configureFiles() throws Exception {
        configureByFiles(null, "/testFileForBaseProperties.json", "/basePropertiesSchema.json");
      }

      @Override
      public void doCheck() {
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
        Assert.assertEquals("basePropertiesSchema.json", resolve.getContainingFile().getName());
        Assert.assertEquals("\"baseEnum\"", resolve.getText());
      }
    });
  }

  @NotNull
  private static String getModuleDir(@NotNull final Project project) {
    String moduleDir = null;
    VirtualFile[] children = project.getBaseDir().getChildren();
    for (VirtualFile child : children) {
      if (child.isDirectory()) {
        moduleDir = child.getName();
        break;
      }
    }
    Assert.assertNotNull(moduleDir);
    return moduleDir;
  }
}
