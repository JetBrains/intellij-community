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
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonValue;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.AreaPicoContainer;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.jetbrains.jsonSchema.extension.schema.JsonSchemaReferenceContributor;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import com.jetbrains.jsonSchema.schemaFile.TestJsonSchemaMappingsProjectConfiguration;
import org.junit.Assert;

import java.util.Collections;
import java.util.List;

/**
 * @author Irina.Chernushina on 3/28/2016.
 */
public class JsonSchemaCrossReferencesTest extends JsonSchemaHeavyAbstractTest {
  private final static String BASE_PATH = "/tests/testData/jsonSchema/crossReferences";

  @Override
  protected String getBasePath() {
    return BASE_PATH;
  }

  @CanChangeDocumentDuringHighlighting
  public void testJsonSchemaCrossReferenceCompletion() throws Exception {
    skeleton(new Callback() {
      @Override
      public void doCheck() {
        checkCompletion("\"one\"", "\"two\"");
      }

      @Override
      public void configureFiles() throws Exception {
        configureByFiles(null, "/completion.json", "/baseSchema.json", "/inheritedSchema.json");
      }

      @Override
      public void registerSchemes() {
        final String moduleDir = getModuleDir(getProject());

        final UserDefinedJsonSchemaConfiguration base =
          new UserDefinedJsonSchemaConfiguration("base", moduleDir + "/baseSchema.json", false, Collections.emptyList());
        addSchema(base);

        final UserDefinedJsonSchemaConfiguration inherited
          = new UserDefinedJsonSchemaConfiguration("inherited", moduleDir + "/inheritedSchema.json", false,
                                                   Collections.singletonList(new UserDefinedJsonSchemaConfiguration.Item("*.json", true, false)));

        addSchema(inherited);
      }
    });
  }

  private void checkCompletion(String... strings) {
    assertStringItems(strings);

    LookupImpl lookup = getActiveLookup();
    if (lookup != null) lookup.hide();
    JsonSchemaService.Impl.get(getProject()).reset();
    doHighlighting();
    complete();
    assertStringItems(strings);
  }

  @CanChangeDocumentDuringHighlighting
  public void testRefreshSchemaCompletionSimpleVariant() throws Exception {
    skeleton(new Callback() {
      private String myModuleDir;

      @Override
      public void registerSchemes() {
        myModuleDir = getModuleDir(getProject());

        final UserDefinedJsonSchemaConfiguration base =
          new UserDefinedJsonSchemaConfiguration("base", myModuleDir + "/basePropertiesSchema.json", false,
                                                 Collections.singletonList(new UserDefinedJsonSchemaConfiguration.Item("*.json", true, false)));
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

  @CanChangeDocumentDuringHighlighting
  public void testJsonSchemaCrossReferenceCompletionWithSchemaEditing() throws Exception {
    skeleton(new Callback() {
      private String myModuleDir;

      @Override
      public void registerSchemes() {
        myModuleDir = getModuleDir(getProject());

        final UserDefinedJsonSchemaConfiguration base =
          new UserDefinedJsonSchemaConfiguration("base", myModuleDir + "/baseSchema.json", false, Collections.emptyList());
        addSchema(base);

        final UserDefinedJsonSchemaConfiguration inherited
          = new UserDefinedJsonSchemaConfiguration("inherited", myModuleDir + "/inheritedSchema.json", false,
                                                   Collections.singletonList(new UserDefinedJsonSchemaConfiguration.Item("*.json", true, false)));

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
    JsonSchemaService.Impl.get(getProject()).reset();

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

  @CanChangeDocumentDuringHighlighting
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

        final UserDefinedJsonSchemaConfiguration base =
          new UserDefinedJsonSchemaConfiguration("base", moduleDir + "/localRefSchema.json", false, Collections.emptyList());
        addSchema(base);

        final UserDefinedJsonSchemaConfiguration inherited
          = new UserDefinedJsonSchemaConfiguration("inherited", moduleDir + "/referencingSchema.json", false, Collections.emptyList());

        addSchema(inherited);
      }
    });
  }

  @CanChangeDocumentDuringHighlighting
  public void testJsonSchemaGlobalRefsCrossResolve() throws Exception {
    skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        final String moduleDir = getModuleDir(getProject());

        AreaPicoContainer container = Extensions.getArea(getProject()).getPicoContainer();
        final String key = JsonSchemaMappingsProjectConfiguration.class.getName();
        container.unregisterComponent(key);
        container.registerComponentImplementation(key, TestJsonSchemaMappingsProjectConfiguration.class);

        final UserDefinedJsonSchemaConfiguration inherited
          = new UserDefinedJsonSchemaConfiguration("inherited", moduleDir + "/referencingGlobalSchema.json", false, Collections.emptyList());

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

  @CanChangeDocumentDuringHighlighting
  public void testJson2SchemaPropertyResolve() throws Exception {
    skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        final String moduleDir = getModuleDir(getProject());
        final UserDefinedJsonSchemaConfiguration inherited
          = new UserDefinedJsonSchemaConfiguration("inherited", moduleDir + "/basePropertiesSchema.json", false,
                                                   Collections.singletonList(
                                                                 new UserDefinedJsonSchemaConfiguration.Item("*.json", true, false)));

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
          if (JsonSchemaReferenceContributor.PROPERTY_NAME_PATTERN.accepts(element)) {
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

  @CanChangeDocumentDuringHighlighting
  public void testFindRefInOtherFile() throws Exception {
    skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        final String moduleDir = getModuleDir(getProject());
        addSchema(new UserDefinedJsonSchemaConfiguration("one", moduleDir + "/refToDefinitionInFileSchema.json", false, Collections.emptyList()));
        addSchema(new UserDefinedJsonSchemaConfiguration("two", moduleDir + "/definitionsSchema.json", false, Collections.emptyList()));
      }

      @Override
      public void configureFiles() throws Exception {
        configureByFiles(null, "/refToDefinitionInFileSchema.json", "/definitionsSchema.json");
      }

      @Override
      public void doCheck() {
        int offset = myEditor.getCaretModel().getPrimaryCaret().getOffset();
        final PsiReference referenceAt = myFile.findReferenceAt(offset);
        Assert.assertNotNull(referenceAt);
        final PsiElement resolve = referenceAt.resolve();
        Assert.assertNotNull(resolve);
        Assert.assertEquals("definitionsSchema.json", resolve.getContainingFile().getName());
        Assert.assertEquals("\"findMe\"", resolve.getText());
      }
    });
  }

  @CanChangeDocumentDuringHighlighting
  public void testFindRefToOtherFile() throws Exception {
    skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        final String moduleDir = getModuleDir(getProject());
        addSchema(new UserDefinedJsonSchemaConfiguration("one", moduleDir + "/refToOtherFileSchema.json", false, Collections.emptyList()));
        addSchema(new UserDefinedJsonSchemaConfiguration("two", moduleDir + "/definitionsSchema.json", false, Collections.emptyList()));
      }

      @Override
      public void configureFiles() throws Exception {
        configureByFiles(null, "/refToOtherFileSchema.json", "/definitionsSchema.json");
      }

      @Override
      public void doCheck() {
        int offset = myEditor.getCaretModel().getPrimaryCaret().getOffset();
        final PsiReference referenceAt = myFile.findReferenceAt(offset);
        Assert.assertNotNull(referenceAt);
        final PsiElement resolve = referenceAt.resolve();
        Assert.assertNotNull(resolve);
        Assert.assertEquals("definitionsSchema.json", resolve.getContainingFile().getName());
      }
    });
  }

  @CanChangeDocumentDuringHighlighting
  public void testNavigateToPropertyDefinitionInPackageJsonSchema() throws Exception {
    skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        final String moduleDir = getModuleDir(getProject());
        final List<UserDefinedJsonSchemaConfiguration.Item> patterns = Collections.singletonList(
          new UserDefinedJsonSchemaConfiguration.Item("package.json", true, false));
        addSchema(new UserDefinedJsonSchemaConfiguration("one", moduleDir + "/packageJsonSchema.json", false, patterns));
      }

      @Override
      public void configureFiles() throws Exception {
        configureByFiles(null, "/package.json", "/packageJsonSchema.json");
      }

      @Override
      public void doCheck() {
        final String text = myFile.getText();
        final int indexOf = text.indexOf("dependencies");
        assertTrue(indexOf > 0);
        final PsiReference referenceAt = myFile.findReferenceAt(indexOf);
        Assert.assertNotNull(referenceAt);
        final PsiElement resolve = referenceAt.resolve();
        Assert.assertNotNull(resolve);
        Assert.assertEquals("packageJsonSchema.json", resolve.getContainingFile().getName());
        Assert.assertEquals("\"dependencies\"", resolve.getText());
      }
    });
  }

  @CanChangeDocumentDuringHighlighting
  public void testNavigateToPropertyDefinitionNestedDefinitions() throws Exception {
    skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        final String moduleDir = getModuleDir(getProject());
        final List<UserDefinedJsonSchemaConfiguration.Item> patterns = Collections.singletonList(
          new UserDefinedJsonSchemaConfiguration.Item("testNestedDefinitionsNavigation.json", true, false));
        addSchema(new UserDefinedJsonSchemaConfiguration("one", moduleDir + "/nestedDefinitionsSchema.json", false, patterns));
      }

      @Override
      public void configureFiles() throws Exception {
        configureByFiles(null, "/testNestedDefinitionsNavigation.json", "/nestedDefinitionsSchema.json");
      }

      @Override
      public void doCheck() {
        int offset = myEditor.getCaretModel().getPrimaryCaret().getOffset();
        final PsiReference referenceAt = myFile.findReferenceAt(offset);
        Assert.assertNotNull(referenceAt);
        final PsiElement resolve = referenceAt.resolve();
        Assert.assertNotNull(resolve);
        Assert.assertEquals("nestedDefinitionsSchema.json", resolve.getContainingFile().getName());
        Assert.assertEquals("\"definitions\"", resolve.getText());
      }
    });
  }

  @CanChangeDocumentDuringHighlighting
  public void testNavigateToAllOfOneOfDefinitions() throws Exception {
    skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        final String moduleDir = getModuleDir(getProject());
        final List<UserDefinedJsonSchemaConfiguration.Item> patterns = Collections.singletonList(
          new UserDefinedJsonSchemaConfiguration.Item("testNestedAllOfOneOfDefinitions.json", true, false));
        addSchema(new UserDefinedJsonSchemaConfiguration("one", moduleDir + "/nestedAllOfOneOfDefinitionsSchema.json", false, patterns));
      }

      @Override
      public void configureFiles() throws Exception {
        configureByFiles(null, "/testNestedAllOfOneOfDefinitions.json", "/nestedAllOfOneOfDefinitionsSchema.json");
      }

      @Override
      public void doCheck() {
        int offset = myEditor.getCaretModel().getPrimaryCaret().getOffset();
        final PsiReference referenceAt = myFile.findReferenceAt(offset);
        Assert.assertNotNull(referenceAt);
        final PsiElement resolve = referenceAt.resolve();
        Assert.assertNotNull(resolve);
        Assert.assertEquals("nestedAllOfOneOfDefinitionsSchema.json", resolve.getContainingFile().getName());
        Assert.assertEquals("\"begriff\"", resolve.getText());
      }
    });
  }

  @CanChangeDocumentDuringHighlighting
  public void testNestedAllOneAnyWithInheritanceNavigation() throws Exception {
    final String prefix = "nestedAllOneAnyWithInheritance/";
    skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        final String moduleDir = getModuleDir(getProject());
        addSchema(new UserDefinedJsonSchemaConfiguration("one", moduleDir + "/baseSchema.json", false, Collections.emptyList()));
        final List<UserDefinedJsonSchemaConfiguration.Item> patterns = Collections.singletonList(
          new UserDefinedJsonSchemaConfiguration.Item("testNavigation.json", true, false));
        addSchema(new UserDefinedJsonSchemaConfiguration("two", moduleDir + "/referentSchema.json", false, patterns));
      }

      @Override
      public void configureFiles() throws Exception {
        configureByFiles(null, prefix + "testNavigation.json", prefix + "baseSchema.json", prefix + "referentSchema.json");
      }

      @Override
      public void doCheck() {
        int offset = myEditor.getCaretModel().getPrimaryCaret().getOffset();
        final PsiReference referenceAt = myFile.findReferenceAt(offset);
        Assert.assertNotNull(referenceAt);
        final PsiElement resolve = referenceAt.resolve();
        Assert.assertNotNull(resolve);
        Assert.assertEquals("baseSchema.json", resolve.getContainingFile().getName());
        Assert.assertEquals("\"findMe\"", resolve.getText());
      }
    });
  }

  @CanChangeDocumentDuringHighlighting
  public void testNestedAllOneAnyWithInheritanceCompletion() throws Exception {
    final String prefix = "nestedAllOneAnyWithInheritance/";
    skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        final String moduleDir = getModuleDir(getProject());
        addSchema(new UserDefinedJsonSchemaConfiguration("one", moduleDir + "/baseSchema.json", false, Collections.emptyList()));
        final List<UserDefinedJsonSchemaConfiguration.Item> patterns = Collections.singletonList(
          new UserDefinedJsonSchemaConfiguration.Item("testCompletion.json", true, false));
        addSchema(new UserDefinedJsonSchemaConfiguration("two", moduleDir + "/referentSchema.json", false, patterns));
      }

      @Override
      public void configureFiles() throws Exception {
        configureByFiles(null, prefix + "testCompletion.json", prefix + "baseSchema.json", prefix + "referentSchema.json");
      }

      @Override
      public void doCheck() {
        checkCompletion("1","2");
      }
    });
  }

  @CanChangeDocumentDuringHighlighting
  public void testNestedAllOneAnyWithInheritanceHighlighting() throws Exception {
    final String prefix = "nestedAllOneAnyWithInheritance/";
    skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        final String moduleDir = getModuleDir(getProject());
        addSchema(new UserDefinedJsonSchemaConfiguration("one", moduleDir + "/baseSchema.json", false, Collections.emptyList()));
        final List<UserDefinedJsonSchemaConfiguration.Item> patterns = Collections.singletonList(
          new UserDefinedJsonSchemaConfiguration.Item("testHighlighting.json", true, false));
        addSchema(new UserDefinedJsonSchemaConfiguration("two", moduleDir + "/referentSchema.json", false, patterns));
      }

      @Override
      public void configureFiles() throws Exception {
        configureByFiles(null, prefix + "testHighlighting.json", prefix + "baseSchema.json", prefix + "referentSchema.json");
      }

      @Override
      public void doCheck() {
        doDoTest(true, false);
      }
    });
  }

  @CanChangeDocumentDuringHighlighting
  public void testNavigateToDefinitionByRef() throws Exception {
    skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        final String moduleDir = getModuleDir(getProject());
        addSchema(new UserDefinedJsonSchemaConfiguration("one", moduleDir + "/withReferenceToDefinitionSchema.json", false, Collections.emptyList()));
      }

      @Override
      public void configureFiles() throws Exception {
        configureByFiles(null, "withReferenceToDefinitionSchema.json");
      }

      @Override
      public void doCheck() {
        int offset = myEditor.getCaretModel().getPrimaryCaret().getOffset();
        final PsiReference referenceAt = myFile.findReferenceAt(offset);
        Assert.assertNotNull(referenceAt);
        final PsiElement resolve = referenceAt.resolve();
        Assert.assertNotNull(resolve);
        Assert.assertEquals("\"findDefinition\"", resolve.getText());
        final PsiElement parent = resolve.getParent();
        Assert.assertTrue(parent instanceof JsonProperty);
        final JsonValue value = ((JsonProperty)parent).getValue();
        Assert.assertTrue(value instanceof JsonObject);
        final JsonProperty anEnum = ((JsonObject)value).findProperty("enum");
        Assert.assertNotNull(anEnum);
        Assert.assertEquals("[1,4,8]", anEnum.getValue().getText());
      }
    });
  }

  @CanChangeDocumentDuringHighlighting
  public void testNavigateToDefinitionByRefInFileWithIncorrectReference() throws Exception {
    skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        final String moduleDir = getModuleDir(getProject());
        addSchema(new UserDefinedJsonSchemaConfiguration("one", moduleDir + "/withIncorrectReferenceSchema.json", false, Collections.emptyList()));
      }

      @Override
      public void configureFiles() throws Exception {
        configureByFiles(null, "withIncorrectReferenceSchema.json");
      }

      @Override
      public void doCheck() {
        final String midia = "midia";
        checkNavigationIntoDefinition(midia);
      }
    });
  }

  private void checkNavigationIntoDefinition(String name) {
    int offset = myEditor.getCaretModel().getPrimaryCaret().getOffset();
    final PsiElement element = myFile.findElementAt(offset);
    Assert.assertNotNull(element);

    final PsiReference referenceAt = myFile.findReferenceAt(offset);
    Assert.assertNotNull(referenceAt);
    final PsiElement resolve = referenceAt.resolve();
    Assert.assertNotNull(resolve);
    Assert.assertEquals("\"" + name + "\"", resolve.getText());
    final PsiElement parent = resolve.getParent();
    Assert.assertTrue(parent instanceof JsonProperty);
    Assert.assertEquals(name, ((JsonProperty) parent).getName());
    Assert.assertTrue(parent.getParent().getParent() instanceof JsonProperty);
    Assert.assertEquals(JsonSchemaObject.DEFINITIONS, ((JsonProperty) parent.getParent().getParent()).getName());
  }

  @CanChangeDocumentDuringHighlighting
  public void testInsideCycledSchemaNavigation() throws Exception {
    skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        final String moduleDir = getModuleDir(getProject());
        addSchema(new UserDefinedJsonSchemaConfiguration("one", moduleDir + "/insideCycledSchemaNavigationSchema.json",
                                                         false, Collections.emptyList()));
      }

      @Override
      public void configureFiles() throws Exception {
        configureByFiles(null, "insideCycledSchemaNavigationSchema.json");
      }

      @Override
      public void doCheck() {
        int offset = myEditor.getCaretModel().getPrimaryCaret().getOffset();
        final PsiElement element = myFile.findElementAt(offset);
        Assert.assertNotNull(element);

        final PsiReference referenceAt = myFile.findReferenceAt(offset);
        Assert.assertNotNull(referenceAt);
        // todo in debug it is seen, that during resolve we come to the state "cyclic definition" => write a more specific test about that
        // todo this current test checks that it works in reasonable time without timeout
        final PsiElement resolve = referenceAt.resolve();
        Assert.assertNull(resolve);
      }
    });
  }

  @CanChangeDocumentDuringHighlighting
  public void testNavigationIntoCycledSchema() throws Exception {
    skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        final String moduleDir = getModuleDir(getProject());
        final List<UserDefinedJsonSchemaConfiguration.Item> patterns = Collections.singletonList(
          new UserDefinedJsonSchemaConfiguration.Item("*.json", true, false));
        addSchema(new UserDefinedJsonSchemaConfiguration("one", moduleDir + "/cycledSchema.json", false, patterns));
      }

      @Override
      public void configureFiles() throws Exception {
        configureByFiles(null, "testNavigationIntoCycled.json", "cycledSchema.json");
      }

      @Override
      public void doCheck() {
        int offset = myEditor.getCaretModel().getPrimaryCaret().getOffset();
        final PsiElement element = myFile.findElementAt(offset);
        Assert.assertNotNull(element);

        final PsiReference referenceAt = myFile.findReferenceAt(offset);
        Assert.assertNotNull(referenceAt);
        final PsiElement resolve = referenceAt.resolve();
        Assert.assertNotNull(resolve);
        // no actual resolve; see comment to the upper test about cyclic definitions
        Assert.assertEquals(myFile, resolve.getContainingFile());
      }
    });
  }
}
