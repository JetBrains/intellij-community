// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.json.JsonFileType;
import com.intellij.json.psi.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.JsonSchemaProjectSelfProviderFactory;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion;
import com.jetbrains.jsonSchema.impl.inspections.JsonSchemaComplianceInspection;
import com.jetbrains.jsonSchema.schemaFile.TestJsonSchemaMappingsProjectConfiguration;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.util.*;
import java.util.stream.Collectors;

import static com.jetbrains.jsonSchema.impl.light.SchemaKeywordsKt.JSON_DEFINITIONS;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywordsKt.JSON_PROPERTIES;

public class JsonSchemaCrossReferencesTest extends JsonSchemaHeavyAbstractTest {
  private final static String BASE_PATH = "/tests/testData/jsonSchema/crossReferences";

  @Override
  protected String getBasePath() {
    return BASE_PATH;
  }

  public void testJsonSchemaCrossReferenceCompletion() throws Exception {
    skeleton(new Callback() {
      @Override
      public void doCheck() {
        checkCompletion("\"one\"", "\"two\"");
      }

      @Override
      public void configureFiles() {
        myFixture.configureByFiles("/completion.json", "/baseSchema.json", "/inheritedSchema.json");
      }

      @Override
      public void registerSchemes() {
        final UserDefinedJsonSchemaConfiguration base =
          new UserDefinedJsonSchemaConfiguration("base", JsonSchemaVersion.SCHEMA_4, getUrlUnderTestRoot("baseSchema.json"), false, Collections.emptyList());
        addSchema(base);

        final UserDefinedJsonSchemaConfiguration inherited
          = new UserDefinedJsonSchemaConfiguration("inherited", JsonSchemaVersion.SCHEMA_4, getUrlUnderTestRoot("inheritedSchema.json"), false,
                                                   Collections
                                                     .singletonList(new UserDefinedJsonSchemaConfiguration.Item("*.json", true, false))
        );

        addSchema(inherited);
      }
    });
  }

  private void checkCompletion(String... strings) {
    assertStringItems(strings);

    JsonSchemaService.Impl.get(getProject()).reset();
    myFixture.doHighlighting();
    complete();
    assertStringItems(strings);
  }

  public void testRefreshSchemaCompletionSimpleVariant() throws Exception {
    skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        final UserDefinedJsonSchemaConfiguration base =
          new UserDefinedJsonSchemaConfiguration("base", JsonSchemaVersion.SCHEMA_4, getUrlUnderTestRoot("basePropertiesSchema.json"), false,
                                                 Collections
                                                   .singletonList(new UserDefinedJsonSchemaConfiguration.Item("*.json", true, false))
          );
        addSchema(base);
      }

      @Override
      public void configureFiles() {
        myFixture.configureByFiles("/baseCompletion.json", "/basePropertiesSchema.json");
      }

      @Override
      public void doCheck() {
        final VirtualFile moduleFile = locateFileUnderTestRoot("/");
        assertNotNull(moduleFile);
        checkSchemaCompletion(moduleFile, "basePropertiesSchema.json");
      }
    });
  }

  public void testJsonSchemaCrossReferenceCompletionWithSchemaEditing() throws Exception {
    skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        final UserDefinedJsonSchemaConfiguration base =
          new UserDefinedJsonSchemaConfiguration("base", JsonSchemaVersion.SCHEMA_4, getUrlUnderTestRoot("/baseSchema.json"), false, Collections.emptyList());
        addSchema(base);

        final UserDefinedJsonSchemaConfiguration inherited
          = new UserDefinedJsonSchemaConfiguration("inherited", JsonSchemaVersion.SCHEMA_4, getUrlUnderTestRoot("/inheritedSchema.json"), false,
                                                   Collections
                                                     .singletonList(new UserDefinedJsonSchemaConfiguration.Item("*.json", true, false))
        );

        addSchema(inherited);
      }

      @Override
      public void configureFiles() {
        myFixture.configureByFiles("/completion.json", "/baseSchema.json", "/inheritedSchema.json");
      }

      @Override
      public void doCheck() {
        final VirtualFile moduleFile = locateFileUnderTestRoot("/");
        assertNotNull(moduleFile);
        checkSchemaCompletion(moduleFile, "baseSchema.json");
      }
    });
  }

  private void checkSchemaCompletion(VirtualFile moduleFile, final String fileName) {
    myFixture.doHighlighting();
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

    CommandProcessor.getInstance().runUndoTransparentAction(() ->
      ApplicationManager.getApplication().runWriteAction(() -> {
        document.replaceString(start, start + str.length(), "\"enum\": [\"one1\", \"two1\"]");
        fileDocumentManager.saveAllDocuments();
      }));
    JsonSchemaService.Impl.get(getProject()).reset();

    myFixture.doHighlighting();
    complete();
    assertStringItems("\"one1\"", "\"two1\"");

    JsonSchemaService.Impl.get(getProject()).reset();
    myFixture.doHighlighting();
    complete();
    assertStringItems("\"one1\"", "\"two1\"");
  }

  public void testJsonSchemaRefsCrossResolve() throws Exception {
    skeleton(new Callback() {
      @Override
      public void doCheck() {
        int offset = getCaretOffset();
        final PsiReference referenceAt = myFixture.getFile().findReferenceAt(offset);
        Assert.assertNotNull(referenceAt);
        final PsiElement resolve = referenceAt.resolve();
        Assert.assertNotNull(resolve);
        Assert.assertEquals("""
                              {
                                    "type": "string",
                                    "enum": ["one", "two"]
                                  }""", resolve.getText());
      }

      @Override
      public void configureFiles() {
        myFixture.configureByFiles("/referencingSchema.json", "/localRefSchema.json");
      }

      @Override
      public void registerSchemes() {
        final UserDefinedJsonSchemaConfiguration base =
          new UserDefinedJsonSchemaConfiguration("base", JsonSchemaVersion.SCHEMA_4, getUrlUnderTestRoot("/localRefSchema.json"), false, Collections.emptyList());
        addSchema(base);

        final UserDefinedJsonSchemaConfiguration inherited
          = new UserDefinedJsonSchemaConfiguration("inherited", JsonSchemaVersion.SCHEMA_4, getUrlUnderTestRoot("/referencingSchema.json"), false,
                                                   Collections.emptyList()
        );

        addSchema(inherited);
      }
    });
  }

  public void testJsonSchemaGlobalRefsCrossResolve() throws Exception {
    skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        ServiceContainerUtil.replaceService(getProject(), JsonSchemaMappingsProjectConfiguration.class, new TestJsonSchemaMappingsProjectConfiguration(getProject()), getTestRootDisposable());

        final UserDefinedJsonSchemaConfiguration inherited
          = new UserDefinedJsonSchemaConfiguration("inherited", JsonSchemaVersion.SCHEMA_4, getUrlUnderTestRoot("/referencingGlobalSchema.json"), false,
                                                   Collections.emptyList()
        );

        addSchema(inherited);
      }

      @Override
      public void configureFiles() {
        myFixture.configureByFiles("/referencingGlobalSchema.json");
      }

      @Override
      public void doCheck() {
        int offset = getCaretOffset();
        final PsiReference referenceAt = myFixture.getFile().findReferenceAt(offset);
        Assert.assertNotNull(referenceAt);
        final PsiElement resolve = referenceAt.resolve();
        Assert.assertNotNull(resolve);
        Assert.assertTrue(StringUtil.equalsIgnoreWhitespaces("""
                                                               {
                                                                           "type": "array",
                                                                           "minItems": 1,
                                                                           "uniqueItems": true
                                                                       }""", resolve.getText()));
      }
    });
  }

  public void testJson2SchemaPropertyResolve() throws Exception {
    skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        final UserDefinedJsonSchemaConfiguration inherited
          = new UserDefinedJsonSchemaConfiguration("inherited", JsonSchemaVersion.SCHEMA_4, getUrlUnderTestRoot("/basePropertiesSchema.json"), false,
                                                   Collections.singletonList(
                                                     new UserDefinedJsonSchemaConfiguration.Item("*.json", true, false))
        );

        addSchema(inherited);
      }

      @Override
      public void configureFiles() {
        myFixture.configureByFiles("/testFileForBaseProperties.json", "/basePropertiesSchema.json");
      }

      @Override
      public void doCheck() {
        int offset = getCaretOffset();
        final PsiElement resolve = GotoDeclarationAction.findTargetElement(getProject(), myFixture.getEditor(), offset);
        Assert.assertNotNull(resolve);
        Assert.assertEquals("basePropertiesSchema.json", resolve.getContainingFile().getName());
        Assert.assertEquals("\"baseEnum\"", resolve.getText());
      }
    });
  }

  public void testFindRefInOtherFile() throws Exception {
    skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        addSchema(new UserDefinedJsonSchemaConfiguration("one", JsonSchemaVersion.SCHEMA_4, getUrlUnderTestRoot("/refToDefinitionInFileSchema.json"), false,
                                                         Collections.emptyList()));
        addSchema(
          new UserDefinedJsonSchemaConfiguration("two", JsonSchemaVersion.SCHEMA_4, getUrlUnderTestRoot("/definitionsSchema.json"), false, Collections.emptyList()));
      }

      @Override
      public void configureFiles() {
        myFixture.configureByFiles("/refToDefinitionInFileSchema.json", "/definitionsSchema.json");
      }

      @Override
      public void doCheck() {
        int offset = getCaretOffset();
        final PsiReference referenceAt = myFixture.getFile().findReferenceAt(offset);
        Assert.assertNotNull(referenceAt);
        final PsiElement resolve = referenceAt.resolve();
        Assert.assertNotNull(resolve);
        Assert.assertEquals("definitionsSchema.json", resolve.getContainingFile().getName());
        Assert.assertEquals("{\"type\": \"object\"}", resolve.getText());
      }
    });
  }

  public void testFindRefToOtherFile() throws Exception {
    skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        addSchema(
          new UserDefinedJsonSchemaConfiguration("one", JsonSchemaVersion.SCHEMA_4, getUrlUnderTestRoot("/refToOtherFileSchema.json"), false, Collections.emptyList()
          ));
        addSchema(
          new UserDefinedJsonSchemaConfiguration("two", JsonSchemaVersion.SCHEMA_4, getUrlUnderTestRoot("/definitionsSchema.json"), false, Collections.emptyList()
          ));
      }

      @Override
      public void configureFiles() {
        myFixture.configureByFiles("/refToOtherFileSchema.json", "/definitionsSchema.json");
      }

      @Override
      public void doCheck() {
        int offset = getCaretOffset();
        final PsiReference referenceAt = myFixture.getFile().findReferenceAt(offset);
        Assert.assertNotNull(referenceAt);
        final PsiElement resolve = referenceAt.resolve();
        Assert.assertNotNull(resolve);
        Assert.assertEquals("definitionsSchema.json", resolve.getContainingFile().getName());
      }
    });
  }

  public void testNavigateToPropertyDefinitionInPackageJsonSchema() throws Exception {
    skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        final List<UserDefinedJsonSchemaConfiguration.Item> patterns = Collections.singletonList(
          new UserDefinedJsonSchemaConfiguration.Item("package.json", true, false));
        addSchema(new UserDefinedJsonSchemaConfiguration("one", JsonSchemaVersion.SCHEMA_4, getUrlUnderTestRoot("/packageJsonSchema.json"), false, patterns));
      }

      @Override
      public void configureFiles() {
        myFixture.configureByFiles("/package.json", "/packageJsonSchema.json");
      }

      @Override
      public void doCheck() {
        final String text = myFixture.getFile().getText();
        final int indexOf = text.indexOf("dependencies");
        assertTrue(indexOf > 0);
        final PsiElement resolve = GotoDeclarationAction.findTargetElement(getProject(), myFixture.getEditor(), indexOf);
        Assert.assertNotNull(resolve);
        Assert.assertEquals("packageJsonSchema.json", resolve.getContainingFile().getName());
        Assert.assertEquals("\"dependencies\"", resolve.getText());
      }
    });
  }

  public void testNavigateToPropertyDefinitionNestedDefinitions() throws Exception {
    skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        final List<UserDefinedJsonSchemaConfiguration.Item> patterns = Collections.singletonList(
          new UserDefinedJsonSchemaConfiguration.Item("testNestedDefinitionsNavigation.json", true, false));
        addSchema(new UserDefinedJsonSchemaConfiguration("one", JsonSchemaVersion.SCHEMA_4, getUrlUnderTestRoot("/nestedDefinitionsSchema.json"), false, patterns));
      }

      @Override
      public void configureFiles() {
        myFixture.configureByFiles("/testNestedDefinitionsNavigation.json", "/nestedDefinitionsSchema.json");
      }

      @Override
      public void doCheck() {
        int offset = getCaretOffset();
        final PsiElement resolve = GotoDeclarationAction.findTargetElement(getProject(), myFixture.getEditor(), offset);
        Assert.assertNotNull(resolve);
        Assert.assertEquals("nestedDefinitionsSchema.json", resolve.getContainingFile().getName());
        Assert.assertEquals("\"definitions\"", resolve.getText());
      }
    });
  }

  public void testNavigateToAllOfOneOfDefinitions() throws Exception {
    skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        final List<UserDefinedJsonSchemaConfiguration.Item> patterns = Collections.singletonList(
          new UserDefinedJsonSchemaConfiguration.Item("testNestedAllOfOneOfDefinitions.json", true, false));
        addSchema(
          new UserDefinedJsonSchemaConfiguration("one", JsonSchemaVersion.SCHEMA_4, getUrlUnderTestRoot("/nestedAllOfOneOfDefinitionsSchema.json"), false, patterns
          ));
      }

      @Override
      public void configureFiles() {
        myFixture.configureByFiles("/testNestedAllOfOneOfDefinitions.json", "/nestedAllOfOneOfDefinitionsSchema.json");
      }

      @Override
      public void doCheck() {
        int offset = getCaretOffset();
        final PsiElement resolve = GotoDeclarationAction.findTargetElement(getProject(), myFixture.getEditor(), offset);
        Assert.assertNotNull(resolve);
        Assert.assertEquals("nestedAllOfOneOfDefinitionsSchema.json", resolve.getContainingFile().getName());
        Assert.assertEquals("\"begriff\"", resolve.getText());
      }
    });
  }

  public void testNestedAllOneAnyWithInheritanceNavigation() throws Exception {
    final String prefix = "nestedAllOneAnyWithInheritance/";
    skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        addSchema(new UserDefinedJsonSchemaConfiguration("one", JsonSchemaVersion.SCHEMA_4, getUrlUnderTestRoot(prefix + "/baseSchema.json"), false, Collections.emptyList()));
        final List<UserDefinedJsonSchemaConfiguration.Item> patterns = Collections.singletonList(
          new UserDefinedJsonSchemaConfiguration.Item("testNavigation.json", true, false));
        addSchema(new UserDefinedJsonSchemaConfiguration("two", JsonSchemaVersion.SCHEMA_4, getUrlUnderTestRoot(prefix + "/referentSchema.json"), false, patterns));
      }

      @Override
      public void configureFiles() {
        myFixture.configureByFiles(prefix + "testNavigation.json", prefix + "baseSchema.json", prefix + "referentSchema.json");
      }

      @Override
      public void doCheck() {
        int offset = getCaretOffset();
        final PsiElement resolve = GotoDeclarationAction.findTargetElement(getProject(), myFixture.getEditor(), offset);
        Assert.assertNotNull(resolve);
        Assert.assertEquals("baseSchema.json", resolve.getContainingFile().getName());
        Assert.assertEquals("\"findMe\"", resolve.getText());
      }
    });
  }

  public void testNestedAllOneAnyWithInheritanceCompletion() throws Exception {
    final String prefix = "nestedAllOneAnyWithInheritance/";
    skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        addSchema(new UserDefinedJsonSchemaConfiguration("one", JsonSchemaVersion.SCHEMA_4, getUrlUnderTestRoot(prefix + "/baseSchema.json"), false, Collections.emptyList()));
        final List<UserDefinedJsonSchemaConfiguration.Item> patterns = Collections.singletonList(
          new UserDefinedJsonSchemaConfiguration.Item("testCompletion.json", true, false));
        addSchema(new UserDefinedJsonSchemaConfiguration("two", JsonSchemaVersion.SCHEMA_4, getUrlUnderTestRoot(prefix + "/referentSchema.json"), false, patterns));
      }

      @Override
      public void configureFiles() {
        myFixture.configureByFiles(prefix + "testCompletion.json", prefix + "baseSchema.json", prefix + "referentSchema.json");
      }

      @Override
      public void doCheck() {
        checkCompletion("1", "2");
      }
    });
  }

  public void testNestedAllOneAnyWithInheritanceHighlighting() throws Exception {
    final String prefix = "nestedAllOneAnyWithInheritance/";
    myFixture.enableInspections(new JsonSchemaComplianceInspection());
    skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        addSchema(new UserDefinedJsonSchemaConfiguration("one", JsonSchemaVersion.SCHEMA_4, getUrlUnderTestRoot(prefix + "/baseSchema.json"), false, Collections.emptyList()));
        final List<UserDefinedJsonSchemaConfiguration.Item> patterns = Collections.singletonList(
          new UserDefinedJsonSchemaConfiguration.Item("testHighlighting.json", true, false));
        addSchema(new UserDefinedJsonSchemaConfiguration("two", JsonSchemaVersion.SCHEMA_4, getUrlUnderTestRoot(prefix + "/referentSchema.json"), false, patterns));
      }

      @Override
      public void configureFiles() {
        myFixture.configureByFiles(prefix + "testHighlighting.json", prefix + "baseSchema.json", prefix + "referentSchema.json");
      }

      @Override
      public void doCheck() {
        myFixture.testHighlighting(true, false, false);
      }
    });
  }

  public void testNavigateToDefinitionByRef() throws Exception {
    skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        addSchema(new UserDefinedJsonSchemaConfiguration("one", JsonSchemaVersion.SCHEMA_4,
                                                         getUrlUnderTestRoot("/withReferenceToDefinitionSchema.json"), false,
                                                         Collections.emptyList()
        ));
      }

      @Override
      public void configureFiles() {
        myFixture.configureByFiles("withReferenceToDefinitionSchema.json");
      }

      @Override
      public void doCheck() {
        int offset = getCaretOffset();
        final PsiReference referenceAt = myFixture.getFile().findReferenceAt(offset);
        Assert.assertNotNull(referenceAt);
        final PsiElement resolve = referenceAt.resolve();
        Assert.assertNotNull(resolve);
        Assert.assertEquals("""
                              {
                                    "enum": [1,4,8]
                                  }""", resolve.getText());
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

  public void testCompletionInsideSchemaDefinition() throws Exception {
    skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        addSchema(new UserDefinedJsonSchemaConfiguration("one",
                                                         JsonSchemaVersion.SCHEMA_4, getUrlUnderTestRoot("/completionInsideSchemaDefinition.json"), false,
                                                         Collections.emptyList()));
      }

      @Override
      public void configureFiles() {
        myFixture.configureByFiles("completionInsideSchemaDefinition.json");
      }

      @Override
      public void doCheck() {
        final Set<String> strings = Arrays.stream(myItems).map(LookupElement::getLookupString).collect(Collectors.toSet());
        Assert.assertTrue(strings.contains("\"enum\""));
        Assert.assertTrue(strings.contains("\"exclusiveMinimum\""));
        Assert.assertTrue(strings.contains("\"description\""));
      }
    });
  }

  public void testNavigateFromSchemaDefinitionToMainSchema() throws Exception {
    skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        addSchema(new UserDefinedJsonSchemaConfiguration("one",
                                                         JsonSchemaVersion.SCHEMA_4,
                                                         getUrlUnderTestRoot("/navigateFromSchemaDefinitionToMainSchema.json"), false,
                                                         Collections.emptyList()));
      }

      @Override
      public void configureFiles() {
        myFixture.configureByFiles("navigateFromSchemaDefinitionToMainSchema.json");
      }

      @Override
      public void doCheck() {
        int offset = getCaretOffset();
        final PsiElement resolve = GotoDeclarationAction.findTargetElement(getProject(), myFixture.getEditor(), offset);
        Assert.assertNotNull(resolve);
        Assert.assertEquals("\"properties\"", resolve.getText());
        final PsiElement parent = resolve.getParent();
        Assert.assertTrue(parent instanceof JsonProperty);
        Assert.assertEquals("schema.json", resolve.getContainingFile().getName());
      }
    });
  }

  public void testNavigateToRefInsideMainSchema() {
    final JsonSchemaService service = JsonSchemaService.Impl.get(getProject());
    final List<JsonSchemaFileProvider> providers = new JsonSchemaProjectSelfProviderFactory().getProviders(getProject()).stream()
      .filter(it -> it.getSchemaVersion() != JsonSchemaVersion.SCHEMA_2019_09 && it.getSchemaVersion() != JsonSchemaVersion.SCHEMA_2020_12)
      .toList();
    for (JsonSchemaFileProvider provider: providers) {
      final VirtualFile mainSchema = provider.getSchemaFile();
      assertNotNull(mainSchema);
      assertTrue(service.isSchemaFile(mainSchema));

      final PsiFile psi = PsiManager.getInstance(getProject()).findFile(mainSchema);
      Assert.assertNotNull(psi);
      Assert.assertTrue(psi instanceof JsonFile);
      final JsonValue top = ((JsonFile)psi).getTopLevelValue();
      final JsonObject obj = ObjectUtils.tryCast(top, JsonObject.class);
      Assert.assertNotNull(obj);
      final JsonProperty properties = obj.findProperty("properties");
      final JsonObject propObj = ObjectUtils.tryCast(properties.getValue(), JsonObject.class);
      final JsonProperty maxLength = propObj.findProperty("maxLength");
      final JsonObject value = ObjectUtils.tryCast(maxLength.getValue(), JsonObject.class);
      Assert.assertNotNull(value);
      final JsonProperty ref = value.findProperty("$ref");
      Assert.assertNotNull(ref);
      final JsonStringLiteral literal = ObjectUtils.tryCast(ref.getValue(), JsonStringLiteral.class);
      Assert.assertNotNull(literal);

      final PsiReference reference = psi.findReferenceAt(literal.getTextRange().getEndOffset() - 1);
      Assert.assertNotNull(reference);
      String positiveOrNonNegative = ((JsonSchemaProjectSelfProviderFactory.MyJsonSchemaFileProvider)provider)
                                       .getSchemaVersion().equals(JsonSchemaVersion.SCHEMA_4)
        ? "positiveInteger"
        : "nonNegativeInteger";
      Assert.assertEquals("#/definitions/" + positiveOrNonNegative, reference.getCanonicalText());
      final PsiElement resolve = reference.resolve();
      Assert.assertNotNull(resolve);
      Assert.assertTrue(StringUtil.equalsIgnoreWhitespaces("""
                                                             {
                                                                         "type": "integer",
                                                                         "minimum": 0
                                                                     }""", resolve.getText()));
      Assert.assertTrue(resolve.getParent() instanceof JsonProperty);
      Assert.assertEquals(positiveOrNonNegative, ((JsonProperty)resolve.getParent()).getName());
    }
  }

  public void testNavigateToDefinitionByRefInFileWithIncorrectReference() throws Exception {
    skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        addSchema(new UserDefinedJsonSchemaConfiguration("one", JsonSchemaVersion.SCHEMA_4, getUrlUnderTestRoot("/withIncorrectReferenceSchema.json"), false,
                                                         Collections.emptyList()
        ));
      }

      @Override
      public void configureFiles() {
        myFixture.configureByFiles("withIncorrectReferenceSchema.json");
      }

      @Override
      public void doCheck() {
        final String midia = """
          {
                "properties": {
                  "mittel" : {
                    "type": ["integer", "boolean"],
                    "description": "this is found!",
                    "enum": [1,2, false]
                  }
                }
              }""";
        checkNavigationTo(midia, "midia", getCaretOffset(), JSON_DEFINITIONS, true);
      }
    });
  }

  private int getCaretOffset() {
    return myFixture.getEditor().getCaretModel().getPrimaryCaret().getOffset();
  }

  private void checkNavigationTo(@NotNull String resolvedText, @NotNull String name, int offset, @NotNull String base, boolean isReference) {
    final PsiElement resolve = isReference
                               ? myFixture.getFile().findReferenceAt(offset).resolve()
                               : GotoDeclarationAction.findTargetElement(getProject(), myFixture.getEditor(), offset);
    Assert.assertNotNull(resolve);
    Assert.assertEquals(resolvedText, resolve.getText());
    final PsiElement parent = resolve.getParent();
    Assert.assertTrue(parent instanceof JsonProperty);
    Assert.assertEquals(name, ((JsonProperty)parent).getName());
    Assert.assertTrue(parent.getParent().getParent() instanceof JsonProperty);
    Assert.assertEquals(base, ((JsonProperty)parent.getParent().getParent()).getName());
  }

  public void testInsideCycledSchemaNavigation() throws Exception {
    skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        addSchema(new UserDefinedJsonSchemaConfiguration("one", JsonSchemaVersion.SCHEMA_4,
                                                         getUrlUnderTestRoot("/insideCycledSchemaNavigationSchema.json"),
                                                         false, Collections.emptyList()));
      }

      @Override
      public void configureFiles() {
        myFixture.configureByFiles("insideCycledSchemaNavigationSchema.json");
      }

      @Override
      public void doCheck() {
        checkNavigationTo("""
                            {
                                  "$ref": "#/definitions/one"
                                }""", "all", getCaretOffset(), JSON_DEFINITIONS, true);
      }
    });
  }

  public void testNavigationIntoCycledSchema() throws Exception {
    skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        final List<UserDefinedJsonSchemaConfiguration.Item> patterns = Collections.singletonList(
          new UserDefinedJsonSchemaConfiguration.Item("*.json", true, false));
        addSchema(new UserDefinedJsonSchemaConfiguration("one", JsonSchemaVersion.SCHEMA_4, getUrlUnderTestRoot("/cycledSchema.json"), false, patterns));
      }

      @Override
      public void configureFiles() {
        myFixture.configureByFiles("testNavigationIntoCycled.json", "cycledSchema.json");
      }

      @Override
      public void doCheck() {
        checkNavigationTo("\"bbb\"", "bbb", getCaretOffset(), JSON_PROPERTIES, false);
      }
    });
  }

  public void testNavigationWithCompositeDefinitionsObject() throws Exception {
    skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        final List<UserDefinedJsonSchemaConfiguration.Item> patterns = Collections.singletonList(
          new UserDefinedJsonSchemaConfiguration.Item("*.json", true, false));
        addSchema(new UserDefinedJsonSchemaConfiguration("one", JsonSchemaVersion.SCHEMA_4,
                                                         getUrlUnderTestRoot("/navigationWithCompositeDefinitionsObjectSchema.json"), false, patterns));
      }

      @Override
      public void configureFiles() {
        myFixture.configureByFiles("navigationWithCompositeDefinitionsObjectSchema.json");
      }

      @Override
      public void doCheck() {
        final Collection<JsonStringLiteral> strings = PsiTreeUtil.findChildrenOfType(myFixture.getFile(), JsonStringLiteral.class);
        final List<JsonStringLiteral> list = ContainerUtil.filter(strings, expression -> expression.getText().contains("#/definitions"));
        Assert.assertEquals(3, list.size());
        list.forEach(literal -> checkNavigationTo("""
                                                    {
                                                          "type": "object",
                                                          "properties": {
                                                            "id": {
                                                              "type": "string"
                                                            },
                                                            "range": {
                                                              "type": "string"
                                                            }
                                                          }
                                                        }""", "cycle.schema", literal.getTextRange().getEndOffset() - 1,
                                                  JSON_DEFINITIONS, true));
      }
    });
  }

  public void testNavigationIntoWithCompositeDefinitionsObject() throws Exception {
    skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        final List<UserDefinedJsonSchemaConfiguration.Item> patterns = Collections.singletonList(
          new UserDefinedJsonSchemaConfiguration.Item("*.json", true, false));
        addSchema(new UserDefinedJsonSchemaConfiguration("one", JsonSchemaVersion.SCHEMA_4,
                                                         getUrlUnderTestRoot("/navigationWithCompositeDefinitionsObjectSchema.json"), false, patterns));
      }

      @Override
      public void configureFiles() {
        myFixture.configureByFiles("navigationIntoWithCompositeDefinitionsObjectSchema.json",
                         "navigationWithCompositeDefinitionsObjectSchema.json");
      }

      @Override
      public void doCheck() {
        checkNavigationTo("\"id\"", "id", getCaretOffset(), JSON_PROPERTIES, false);
      }
    });
  }

  public void testCompletionWithRootRef() throws Exception {
    skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        final List<UserDefinedJsonSchemaConfiguration.Item> patterns = Collections.singletonList(
          new UserDefinedJsonSchemaConfiguration.Item("*.json", true, false));
        addSchema(new UserDefinedJsonSchemaConfiguration("one", JsonSchemaVersion.SCHEMA_4, getUrlUnderTestRoot("/cycledWithRootRefSchema.json"), false, patterns));
      }

      @Override
      public void configureFiles() {
        myFixture.configureByFiles("completionWithRootRef.json", "cycledWithRootRefSchema.json");
      }

      @Override
      public void doCheck() {
        complete();
        checkCompletion("\"id\"", "\"testProp\"");
      }
    });
  }

  public void testResolveByValuesCombinations() throws Exception {
    skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        final List<UserDefinedJsonSchemaConfiguration.Item> patterns = Collections.singletonList(
          new UserDefinedJsonSchemaConfiguration.Item("*.json", true, false));
        addSchema(new UserDefinedJsonSchemaConfiguration("one", JsonSchemaVersion.SCHEMA_4,
                                                         getUrlUnderTestRoot("/ResolveByValuesCombinationsSchema.json"), false, patterns));
      }

      @Override
      public void configureFiles() {
        myFixture.configureByFile("ResolveByValuesCombinationsSchema.json");
      }

      @Override
      public void doCheck() {
        final List<Trinity<String, String, String>> variants = Arrays.asList(
          Trinity.create("yes", "barkling", "dog"),
          Trinity.create("yes", "meowing", "cat"), Trinity.create("yes", "crowling", "mouse"),
          Trinity.create("not", "apparel", "schrank"), Trinity.create("not", "dinner", "tisch"),
          Trinity.create("not", "rest", "sessel"));
        variants.forEach(
          t -> {
            final PsiFile file = myFixture.configureByText(
              JsonFileType.INSTANCE, String.format("{\"alive\":\"%s\",\n\"feature\":\"%s\"}", t.getFirst(), t.getSecond()));
            final JsonFile jsonFile = ObjectUtils.tryCast(file, JsonFile.class);
            Assert.assertNotNull(jsonFile);
            final JsonObject top = ObjectUtils.tryCast(jsonFile.getTopLevelValue(), JsonObject.class);
            Assert.assertNotNull(top);

            TextRange range = top.findProperty("alive").getNameElement().getTextRange();
            checkNavigationToSchemaVariant("alive", range.getStartOffset() + 1, t.getThird());

            range = top.findProperty("feature").getNameElement().getTextRange();
            checkNavigationToSchemaVariant("feature", range.getStartOffset() + 1, t.getThird());
          }
        );
      }
    });
  }

  private void checkNavigationToSchemaVariant(@NotNull String name, int offset, @NotNull String parentPropertyName) {
    final PsiElement resolve = GotoDeclarationAction.findTargetElement(getProject(), myFixture.getEditor(), offset);
    Assert.assertEquals("\"" + name + "\"", resolve.getText());
    final PsiElement parent = resolve.getParent();
    Assert.assertTrue(parent instanceof JsonProperty);
    Assert.assertEquals(name, ((JsonProperty)parent).getName());
    Assert.assertTrue(parent.getParent().getParent() instanceof JsonProperty);
    final JsonProperty props = (JsonProperty)parent.getParent().getParent();
    Assert.assertEquals("properties", props.getName());
    final JsonProperty parentProperty = ObjectUtils.tryCast(props.getParent().getParent(), JsonProperty.class);
    Assert.assertNotNull(parentProperty);
    Assert.assertEquals(parentPropertyName, parentProperty.getName());
  }
}
