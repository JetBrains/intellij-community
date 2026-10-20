// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.json.JsonFileType;
import com.intellij.json.psi.JsonFile;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.json.psi.JsonValue;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.TestDisposable;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.JsonSchemaProjectSelfProviderFactory;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion;
import com.jetbrains.jsonSchema.impl.inspections.JsonSchemaComplianceInspection;
import com.jetbrains.jsonSchema.schemaFile.TestJsonSchemaMappingsProjectConfiguration;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static com.jetbrains.jsonSchema.JsonSchemaHeavyTestFixtureKt.jsonSchemaHeavyFixture;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywordsKt.JSON_DEFINITIONS;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywordsKt.JSON_PROPERTIES;

@TestApplication
@TestDataPath("$PROJECT_ROOT/community/json/backend/tests/testData/jsonSchema/crossReferences")
public class JsonSchemaCrossReferencesTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);
  private final TestFixture<JsonSchemaHeavyTestFixture> schemaFixture = jsonSchemaHeavyFixture(codeInsightFixture);

  private CodeInsightTestFixture myFixture;
  private JsonSchemaHeavyTestFixture mySchemaFixture;

  @BeforeEach
  void setUp() {
    myFixture = codeInsightFixture.get();
    mySchemaFixture = schemaFixture.get();
  }

  @Test
  public void testJsonSchemaCrossReferenceCompletion() throws Exception {
    mySchemaFixture.skeleton(new JsonSchemaHeavyTestFixture.Callback() {
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
          new UserDefinedJsonSchemaConfiguration("base", JsonSchemaVersion.SCHEMA_4, mySchemaFixture.getUrlUnderTestRoot("baseSchema.json"), false, Collections.emptyList());
        mySchemaFixture.addSchema(base);

        final UserDefinedJsonSchemaConfiguration inherited
          = new UserDefinedJsonSchemaConfiguration("inherited", JsonSchemaVersion.SCHEMA_4, mySchemaFixture.getUrlUnderTestRoot("inheritedSchema.json"), false,
                                                   Collections
                                                     .singletonList(new UserDefinedJsonSchemaConfiguration.Item("*.json", true, false))
        );

        mySchemaFixture.addSchema(inherited);
      }
    });
  }

  private void checkCompletion(String... strings) {
    mySchemaFixture.assertStringItems(strings);

    JsonSchemaService.Impl.get(myFixture.getProject()).reset();
    myFixture.doHighlighting();
    mySchemaFixture.complete();
    mySchemaFixture.assertStringItems(strings);
  }

  @Test
  public void testRefreshSchemaCompletionSimpleVariant() throws Exception {
    mySchemaFixture.skeleton(new JsonSchemaHeavyTestFixture.Callback() {
      @Override
      public void registerSchemes() {
        final UserDefinedJsonSchemaConfiguration base =
          new UserDefinedJsonSchemaConfiguration("base", JsonSchemaVersion.SCHEMA_4, mySchemaFixture.getUrlUnderTestRoot("basePropertiesSchema.json"), false,
                                                 Collections
                                                   .singletonList(new UserDefinedJsonSchemaConfiguration.Item("*.json", true, false))
          );
        mySchemaFixture.addSchema(base);
      }

      @Override
      public void configureFiles() {
        myFixture.configureByFiles("/baseCompletion.json", "/basePropertiesSchema.json");
      }

      @Override
      public void doCheck() {
        final VirtualFile moduleFile = mySchemaFixture.locateFileUnderTestRoot("/");
        Assertions.assertNotNull(moduleFile);
        checkSchemaCompletion(moduleFile, "basePropertiesSchema.json");
      }
    });
  }

  @Test
  public void testJsonSchemaCrossReferenceCompletionWithSchemaEditing() throws Exception {
    mySchemaFixture.skeleton(new JsonSchemaHeavyTestFixture.Callback() {
      @Override
      public void registerSchemes() {
        final UserDefinedJsonSchemaConfiguration base =
          new UserDefinedJsonSchemaConfiguration("base", JsonSchemaVersion.SCHEMA_4, mySchemaFixture.getUrlUnderTestRoot("/baseSchema.json"), false, Collections.emptyList());
        mySchemaFixture.addSchema(base);

        final UserDefinedJsonSchemaConfiguration inherited
          = new UserDefinedJsonSchemaConfiguration("inherited", JsonSchemaVersion.SCHEMA_4, mySchemaFixture.getUrlUnderTestRoot("/inheritedSchema.json"), false,
                                                   Collections
                                                     .singletonList(new UserDefinedJsonSchemaConfiguration.Item("*.json", true, false))
        );

        mySchemaFixture.addSchema(inherited);
      }

      @Override
      public void configureFiles() {
        myFixture.configureByFiles("/completion.json", "/baseSchema.json", "/inheritedSchema.json");
      }

      @Override
      public void doCheck() {
        final VirtualFile moduleFile = mySchemaFixture.locateFileUnderTestRoot("/");
        Assertions.assertNotNull(moduleFile);
        checkSchemaCompletion(moduleFile, "baseSchema.json");
      }
    });
  }

  private void checkSchemaCompletion(VirtualFile moduleFile, final String fileName) {
    myFixture.doHighlighting();
    mySchemaFixture.complete();
    mySchemaFixture.assertStringItems("\"one\"", "\"two\"");

    final VirtualFile baseFile = moduleFile.findChild(fileName);
    Assertions.assertNotNull(baseFile);
    FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
    Document document = ReadAction.computeBlocking(() -> fileDocumentManager.getDocument(baseFile));
    Assertions.assertNotNull(document);
    String str = "\"enum\": [\"one\", \"two\"]";
    int start = ReadAction.computeBlocking(() -> document.getText().indexOf(str));
    Assertions.assertTrue(start > 0);

    EdtTestUtil.runInEdtAndWait(() -> CommandProcessor.getInstance().runUndoTransparentAction(() ->
      ApplicationManager.getApplication().runWriteAction(() -> {
        document.replaceString(start, start + str.length(), "\"enum\": [\"one1\", \"two1\"]");
        fileDocumentManager.saveAllDocuments();
      })));
    JsonSchemaService.Impl.get(myFixture.getProject()).reset();

    myFixture.doHighlighting();
    mySchemaFixture.complete();
    mySchemaFixture.assertStringItems("\"one1\"", "\"two1\"");

    JsonSchemaService.Impl.get(myFixture.getProject()).reset();
    myFixture.doHighlighting();
    mySchemaFixture.complete();
    mySchemaFixture.assertStringItems("\"one1\"", "\"two1\"");
  }

  @Test
  public void testJsonSchemaRefsCrossResolve() throws Exception {
    mySchemaFixture.skeleton(new JsonSchemaHeavyTestFixture.Callback() {
      @Override
      public void doCheck() {
        ReadAction.runBlocking(() -> {
          int offset = getCaretOffset();
          final PsiReference referenceAt = myFixture.getFile().findReferenceAt(offset);
          Assertions.assertNotNull(referenceAt);
          final PsiElement resolve = referenceAt.resolve();
          Assertions.assertNotNull(resolve);
          Assertions.assertEquals("""
                                {
                                      "type": "string",
                                      "enum": ["one", "two"]
                                    }""", resolve.getText());
        });
      }

      @Override
      public void configureFiles() {
        myFixture.configureByFiles("/referencingSchema.json", "/localRefSchema.json");
      }

      @Override
      public void registerSchemes() {
        final UserDefinedJsonSchemaConfiguration base =
          new UserDefinedJsonSchemaConfiguration("base", JsonSchemaVersion.SCHEMA_4, mySchemaFixture.getUrlUnderTestRoot("/localRefSchema.json"), false, Collections.emptyList());
        mySchemaFixture.addSchema(base);

        final UserDefinedJsonSchemaConfiguration inherited
          = new UserDefinedJsonSchemaConfiguration("inherited", JsonSchemaVersion.SCHEMA_4, mySchemaFixture.getUrlUnderTestRoot("/referencingSchema.json"), false,
                                                   Collections.emptyList()
        );

        mySchemaFixture.addSchema(inherited);
      }
    });
  }

  @Test
  public void testJsonSchemaGlobalRefsCrossResolve(@TestDisposable Disposable testRootDisposable) throws Exception {
    mySchemaFixture.skeleton(new JsonSchemaHeavyTestFixture.Callback() {
      @Override
      public void registerSchemes() {
        ServiceContainerUtil.replaceService(myFixture.getProject(), JsonSchemaMappingsProjectConfiguration.class, new TestJsonSchemaMappingsProjectConfiguration(myFixture.getProject()), testRootDisposable);

        final UserDefinedJsonSchemaConfiguration inherited
          = new UserDefinedJsonSchemaConfiguration("inherited", JsonSchemaVersion.SCHEMA_4, mySchemaFixture.getUrlUnderTestRoot("/referencingGlobalSchema.json"), false,
                                                   Collections.emptyList()
        );

        mySchemaFixture.addSchema(inherited);
      }

      @Override
      public void configureFiles() {
        myFixture.configureByFiles("/referencingGlobalSchema.json");
      }

      @Override
      public void doCheck() {
        ReadAction.runBlocking(() -> {
          int offset = getCaretOffset();
          final PsiReference referenceAt = myFixture.getFile().findReferenceAt(offset);
          Assertions.assertNotNull(referenceAt);
          final PsiElement resolve = referenceAt.resolve();
          Assertions.assertNotNull(resolve);
          Assertions.assertTrue(StringUtil.equalsIgnoreWhitespaces("""
                                                                 {
                                                                             "type": "array",
                                                                             "minItems": 1,
                                                                             "uniqueItems": true
                                                                         }""", resolve.getText()));
        });
      }
    });
  }

  @Test
  public void testJson2SchemaPropertyResolve() throws Exception {
    mySchemaFixture.skeleton(new JsonSchemaHeavyTestFixture.Callback() {
      @Override
      public void registerSchemes() {
        final UserDefinedJsonSchemaConfiguration inherited
          = new UserDefinedJsonSchemaConfiguration("inherited", JsonSchemaVersion.SCHEMA_4, mySchemaFixture.getUrlUnderTestRoot("/basePropertiesSchema.json"), false,
                                                   Collections.singletonList(
                                                     new UserDefinedJsonSchemaConfiguration.Item("*.json", true, false))
        );

        mySchemaFixture.addSchema(inherited);
      }

      @Override
      public void configureFiles() {
        myFixture.configureByFiles("/testFileForBaseProperties.json", "/basePropertiesSchema.json");
      }

      @Override
      public void doCheck() {
        ReadAction.runBlocking(() -> {
          int offset = getCaretOffset();
          final PsiElement resolve = GotoDeclarationAction.findTargetElement(myFixture.getProject(), myFixture.getEditor(), offset);
          Assertions.assertNotNull(resolve);
          Assertions.assertEquals("basePropertiesSchema.json", resolve.getContainingFile().getName());
          Assertions.assertEquals("\"baseEnum\"", resolve.getText());
        });
      }
    });
  }

  @Test
  public void testFindRefInOtherFile() throws Exception {
    mySchemaFixture.skeleton(new JsonSchemaHeavyTestFixture.Callback() {
      @Override
      public void registerSchemes() {
        mySchemaFixture.addSchema(new UserDefinedJsonSchemaConfiguration("one", JsonSchemaVersion.SCHEMA_4, mySchemaFixture.getUrlUnderTestRoot("/refToDefinitionInFileSchema.json"), false,
                                                         Collections.emptyList()));
        mySchemaFixture.addSchema(
          new UserDefinedJsonSchemaConfiguration("two", JsonSchemaVersion.SCHEMA_4, mySchemaFixture.getUrlUnderTestRoot("/definitionsSchema.json"), false, Collections.emptyList()));
      }

      @Override
      public void configureFiles() {
        myFixture.configureByFiles("/refToDefinitionInFileSchema.json", "/definitionsSchema.json");
      }

      @Override
      public void doCheck() {
        ReadAction.runBlocking(() -> {
          int offset = getCaretOffset();
          final PsiReference referenceAt = myFixture.getFile().findReferenceAt(offset);
          Assertions.assertNotNull(referenceAt);
          final PsiElement resolve = referenceAt.resolve();
          Assertions.assertNotNull(resolve);
          Assertions.assertEquals("definitionsSchema.json", resolve.getContainingFile().getName());
          Assertions.assertEquals("{\"type\": \"object\"}", resolve.getText());
        });
      }
    });
  }

  @Test
  public void testFindRefToOtherFile() throws Exception {
    mySchemaFixture.skeleton(new JsonSchemaHeavyTestFixture.Callback() {
      @Override
      public void registerSchemes() {
        mySchemaFixture.addSchema(
          new UserDefinedJsonSchemaConfiguration("one", JsonSchemaVersion.SCHEMA_4, mySchemaFixture.getUrlUnderTestRoot("/refToOtherFileSchema.json"), false, Collections.emptyList()
          ));
        mySchemaFixture.addSchema(
          new UserDefinedJsonSchemaConfiguration("two", JsonSchemaVersion.SCHEMA_4, mySchemaFixture.getUrlUnderTestRoot("/definitionsSchema.json"), false, Collections.emptyList()
          ));
      }

      @Override
      public void configureFiles() {
        myFixture.configureByFiles("/refToOtherFileSchema.json", "/definitionsSchema.json");
      }

      @Override
      public void doCheck() {
        ReadAction.runBlocking(() -> {
          int offset = getCaretOffset();
          final PsiReference referenceAt = myFixture.getFile().findReferenceAt(offset);
          Assertions.assertNotNull(referenceAt);
          final PsiElement resolve = referenceAt.resolve();
          Assertions.assertNotNull(resolve);
          Assertions.assertEquals("definitionsSchema.json", resolve.getContainingFile().getName());
        });
      }
    });
  }

  @Test
  public void testNavigateToPropertyDefinitionInPackageJsonSchema() throws Exception {
    mySchemaFixture.skeleton(new JsonSchemaHeavyTestFixture.Callback() {
      @Override
      public void registerSchemes() {
        final List<UserDefinedJsonSchemaConfiguration.Item> patterns = Collections.singletonList(
          new UserDefinedJsonSchemaConfiguration.Item("package.json", true, false));
        mySchemaFixture.addSchema(new UserDefinedJsonSchemaConfiguration("one", JsonSchemaVersion.SCHEMA_4, mySchemaFixture.getUrlUnderTestRoot("/packageJsonSchema.json"), false, patterns));
      }

      @Override
      public void configureFiles() {
        myFixture.configureByFiles("/package.json", "/packageJsonSchema.json");
      }

      @Override
      public void doCheck() {
        ReadAction.runBlocking(() -> {
          final String text = myFixture.getFile().getText();
          final int indexOf = text.indexOf("dependencies");
          Assertions.assertTrue(indexOf > 0);
          final PsiElement resolve = GotoDeclarationAction.findTargetElement(myFixture.getProject(), myFixture.getEditor(), indexOf);
          Assertions.assertNotNull(resolve);
          Assertions.assertEquals("packageJsonSchema.json", resolve.getContainingFile().getName());
          Assertions.assertEquals("\"dependencies\"", resolve.getText());
        });
      }
    });
  }

  @Test
  public void testNavigateToPropertyDefinitionNestedDefinitions() throws Exception {
    mySchemaFixture.skeleton(new JsonSchemaHeavyTestFixture.Callback() {
      @Override
      public void registerSchemes() {
        final List<UserDefinedJsonSchemaConfiguration.Item> patterns = Collections.singletonList(
          new UserDefinedJsonSchemaConfiguration.Item("testNestedDefinitionsNavigation.json", true, false));
        mySchemaFixture.addSchema(new UserDefinedJsonSchemaConfiguration("one", JsonSchemaVersion.SCHEMA_4, mySchemaFixture.getUrlUnderTestRoot("/nestedDefinitionsSchema.json"), false, patterns));
      }

      @Override
      public void configureFiles() {
        myFixture.configureByFiles("/testNestedDefinitionsNavigation.json", "/nestedDefinitionsSchema.json");
      }

      @Override
      public void doCheck() {
        ReadAction.runBlocking(() -> {
          int offset = getCaretOffset();
          final PsiElement resolve = GotoDeclarationAction.findTargetElement(myFixture.getProject(), myFixture.getEditor(), offset);
          Assertions.assertNotNull(resolve);
          Assertions.assertEquals("nestedDefinitionsSchema.json", resolve.getContainingFile().getName());
          Assertions.assertEquals("\"definitions\"", resolve.getText());
        });
      }
    });
  }

  @Test
  public void testNavigateToAllOfOneOfDefinitions() throws Exception {
    mySchemaFixture.skeleton(new JsonSchemaHeavyTestFixture.Callback() {
      @Override
      public void registerSchemes() {
        final List<UserDefinedJsonSchemaConfiguration.Item> patterns = Collections.singletonList(
          new UserDefinedJsonSchemaConfiguration.Item("testNestedAllOfOneOfDefinitions.json", true, false));
        mySchemaFixture.addSchema(
          new UserDefinedJsonSchemaConfiguration("one", JsonSchemaVersion.SCHEMA_4, mySchemaFixture.getUrlUnderTestRoot("/nestedAllOfOneOfDefinitionsSchema.json"), false, patterns
          ));
      }

      @Override
      public void configureFiles() {
        myFixture.configureByFiles("/testNestedAllOfOneOfDefinitions.json", "/nestedAllOfOneOfDefinitionsSchema.json");
      }

      @Override
      public void doCheck() {
        ReadAction.runBlocking(() -> {
          int offset = getCaretOffset();
          final PsiElement resolve = GotoDeclarationAction.findTargetElement(myFixture.getProject(), myFixture.getEditor(), offset);
          Assertions.assertNotNull(resolve);
          Assertions.assertEquals("nestedAllOfOneOfDefinitionsSchema.json", resolve.getContainingFile().getName());
          Assertions.assertEquals("\"begriff\"", resolve.getText());
        });
      }
    });
  }

  @Test
  public void testNestedAllOneAnyWithInheritanceNavigation() throws Exception {
    final String prefix = "nestedAllOneAnyWithInheritance/";
    mySchemaFixture.skeleton(new JsonSchemaHeavyTestFixture.Callback() {
      @Override
      public void registerSchemes() {
        mySchemaFixture.addSchema(new UserDefinedJsonSchemaConfiguration("one", JsonSchemaVersion.SCHEMA_4, mySchemaFixture.getUrlUnderTestRoot(prefix + "/baseSchema.json"), false, Collections.emptyList()));
        final List<UserDefinedJsonSchemaConfiguration.Item> patterns = Collections.singletonList(
          new UserDefinedJsonSchemaConfiguration.Item("testNavigation.json", true, false));
        mySchemaFixture.addSchema(new UserDefinedJsonSchemaConfiguration("two", JsonSchemaVersion.SCHEMA_4, mySchemaFixture.getUrlUnderTestRoot(prefix + "/referentSchema.json"), false, patterns));
      }

      @Override
      public void configureFiles() {
        myFixture.configureByFiles(prefix + "testNavigation.json", prefix + "baseSchema.json", prefix + "referentSchema.json");
      }

      @Override
      public void doCheck() {
        ReadAction.runBlocking(() -> {
          int offset = getCaretOffset();
          final PsiElement resolve = GotoDeclarationAction.findTargetElement(myFixture.getProject(), myFixture.getEditor(), offset);
          Assertions.assertNotNull(resolve);
          Assertions.assertEquals("baseSchema.json", resolve.getContainingFile().getName());
          Assertions.assertEquals("\"findMe\"", resolve.getText());
        });
      }
    });
  }

  @Test
  public void testNestedAllOneAnyWithInheritanceCompletion() throws Exception {
    final String prefix = "nestedAllOneAnyWithInheritance/";
    mySchemaFixture.skeleton(new JsonSchemaHeavyTestFixture.Callback() {
      @Override
      public void registerSchemes() {
        mySchemaFixture.addSchema(new UserDefinedJsonSchemaConfiguration("one", JsonSchemaVersion.SCHEMA_4, mySchemaFixture.getUrlUnderTestRoot(prefix + "/baseSchema.json"), false, Collections.emptyList()));
        final List<UserDefinedJsonSchemaConfiguration.Item> patterns = Collections.singletonList(
          new UserDefinedJsonSchemaConfiguration.Item("testCompletion.json", true, false));
        mySchemaFixture.addSchema(new UserDefinedJsonSchemaConfiguration("two", JsonSchemaVersion.SCHEMA_4, mySchemaFixture.getUrlUnderTestRoot(prefix + "/referentSchema.json"), false, patterns));
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

  @Test
  public void testNestedAllOneAnyWithInheritanceHighlighting() throws Exception {
    final String prefix = "nestedAllOneAnyWithInheritance/";
    myFixture.enableInspections(new JsonSchemaComplianceInspection());
    mySchemaFixture.skeleton(new JsonSchemaHeavyTestFixture.Callback() {
      @Override
      public void registerSchemes() {
        mySchemaFixture.addSchema(new UserDefinedJsonSchemaConfiguration("one", JsonSchemaVersion.SCHEMA_4, mySchemaFixture.getUrlUnderTestRoot(prefix + "/baseSchema.json"), false, Collections.emptyList()));
        final List<UserDefinedJsonSchemaConfiguration.Item> patterns = Collections.singletonList(
          new UserDefinedJsonSchemaConfiguration.Item("testHighlighting.json", true, false));
        mySchemaFixture.addSchema(new UserDefinedJsonSchemaConfiguration("two", JsonSchemaVersion.SCHEMA_4, mySchemaFixture.getUrlUnderTestRoot(prefix + "/referentSchema.json"), false, patterns));
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

  @Test
  public void testNavigateToDefinitionByRef() throws Exception {
    mySchemaFixture.skeleton(new JsonSchemaHeavyTestFixture.Callback() {
      @Override
      public void registerSchemes() {
        mySchemaFixture.addSchema(new UserDefinedJsonSchemaConfiguration("one", JsonSchemaVersion.SCHEMA_4,
                                                         mySchemaFixture.getUrlUnderTestRoot("/withReferenceToDefinitionSchema.json"), false,
                                                         Collections.emptyList()
        ));
      }

      @Override
      public void configureFiles() {
        myFixture.configureByFiles("withReferenceToDefinitionSchema.json");
      }

      @Override
      public void doCheck() {
        ReadAction.runBlocking(() -> {
          int offset = getCaretOffset();
          final PsiReference referenceAt = myFixture.getFile().findReferenceAt(offset);
          Assertions.assertNotNull(referenceAt);
          final PsiElement resolve = referenceAt.resolve();
          Assertions.assertNotNull(resolve);
          Assertions.assertEquals("""
                                {
                                      "enum": [1,4,8]
                                    }""", resolve.getText());
          final PsiElement parent = resolve.getParent();
          Assertions.assertTrue(parent instanceof JsonProperty);
          final JsonValue value = ((JsonProperty)parent).getValue();
          Assertions.assertTrue(value instanceof JsonObject);
          final JsonProperty anEnum = ((JsonObject)value).findProperty("enum");
          Assertions.assertNotNull(anEnum);
          Assertions.assertEquals("[1,4,8]", anEnum.getValue().getText());
        });
      }
    });
  }

  @Test
  public void testCompletionInsideSchemaDefinition() throws Exception {
    mySchemaFixture.skeleton(new JsonSchemaHeavyTestFixture.Callback() {
      @Override
      public void registerSchemes() {
        mySchemaFixture.addSchema(new UserDefinedJsonSchemaConfiguration("one",
                                                         JsonSchemaVersion.SCHEMA_4, mySchemaFixture.getUrlUnderTestRoot("/completionInsideSchemaDefinition.json"), false,
                                                         Collections.emptyList()));
      }

      @Override
      public void configureFiles() {
        myFixture.configureByFiles("completionInsideSchemaDefinition.json");
      }

      @Override
      public void doCheck() {
        final Set<String> strings = Arrays.stream(mySchemaFixture.getItems()).map(LookupElement::getLookupString).collect(Collectors.toSet());
        Assertions.assertTrue(strings.contains("\"enum\""));
        Assertions.assertTrue(strings.contains("\"exclusiveMinimum\""));
        Assertions.assertTrue(strings.contains("\"description\""));
      }
    });
  }

  @Test
  public void testNavigateFromSchemaDefinitionToMainSchema() throws Exception {
    mySchemaFixture.skeleton(new JsonSchemaHeavyTestFixture.Callback() {
      @Override
      public void registerSchemes() {
        mySchemaFixture.addSchema(new UserDefinedJsonSchemaConfiguration("one",
                                                         JsonSchemaVersion.SCHEMA_4,
                                                         mySchemaFixture.getUrlUnderTestRoot("/navigateFromSchemaDefinitionToMainSchema.json"), false,
                                                         Collections.emptyList()));
      }

      @Override
      public void configureFiles() {
        myFixture.configureByFiles("navigateFromSchemaDefinitionToMainSchema.json");
      }

      @Override
      public void doCheck() {
        ReadAction.runBlocking(() -> {
          int offset = getCaretOffset();
          final PsiElement resolve = GotoDeclarationAction.findTargetElement(myFixture.getProject(), myFixture.getEditor(), offset);
          Assertions.assertNotNull(resolve);
          Assertions.assertEquals("\"properties\"", resolve.getText());
          final PsiElement parent = resolve.getParent();
          Assertions.assertTrue(parent instanceof JsonProperty);
          Assertions.assertEquals("schema.json", resolve.getContainingFile().getName());
        });
      }
    });
  }

  @Test
  public void testNavigateToRefInsideMainSchema() {
    final JsonSchemaService service = JsonSchemaService.Impl.get(myFixture.getProject());
    final List<JsonSchemaFileProvider> providers = new JsonSchemaProjectSelfProviderFactory().getProviders(myFixture.getProject()).stream()
      .filter(it -> it.getSchemaVersion() != JsonSchemaVersion.SCHEMA_2019_09 && it.getSchemaVersion() != JsonSchemaVersion.SCHEMA_2020_12)
      .toList();
    for (JsonSchemaFileProvider provider: providers) {
      ReadAction.runBlocking(() -> {
        final VirtualFile mainSchema = provider.getSchemaFile();
        Assertions.assertNotNull(mainSchema);
        Assertions.assertTrue(service.isSchemaFile(mainSchema));

        final PsiFile psi = PsiManager.getInstance(myFixture.getProject()).findFile(mainSchema);
        Assertions.assertNotNull(psi);
        Assertions.assertTrue(psi instanceof JsonFile);
        final JsonValue top = ((JsonFile)psi).getTopLevelValue();
        final JsonObject obj = ObjectUtils.tryCast(top, JsonObject.class);
        Assertions.assertNotNull(obj);
        final JsonProperty properties = obj.findProperty("properties");
        final JsonObject propObj = ObjectUtils.tryCast(properties.getValue(), JsonObject.class);
        final JsonProperty maxLength = propObj.findProperty("maxLength");
        final JsonObject value = ObjectUtils.tryCast(maxLength.getValue(), JsonObject.class);
        Assertions.assertNotNull(value);
        final JsonProperty ref = value.findProperty("$ref");
        Assertions.assertNotNull(ref);
        final JsonStringLiteral literal = ObjectUtils.tryCast(ref.getValue(), JsonStringLiteral.class);
        Assertions.assertNotNull(literal);

        final PsiReference reference = psi.findReferenceAt(literal.getTextRange().getEndOffset() - 1);
        Assertions.assertNotNull(reference);
        String positiveOrNonNegative = ((JsonSchemaProjectSelfProviderFactory.MyJsonSchemaFileProvider)provider)
                                         .getSchemaVersion().equals(JsonSchemaVersion.SCHEMA_4)
          ? "positiveInteger"
          : "nonNegativeInteger";
        Assertions.assertEquals("#/definitions/" + positiveOrNonNegative, reference.getCanonicalText());
        final PsiElement resolve = reference.resolve();
        Assertions.assertNotNull(resolve);
        Assertions.assertTrue(StringUtil.equalsIgnoreWhitespaces("""
                                                               {
                                                                           "type": "integer",
                                                                           "minimum": 0
                                                                       }""", resolve.getText()));
        Assertions.assertTrue(resolve.getParent() instanceof JsonProperty);
        Assertions.assertEquals(positiveOrNonNegative, ((JsonProperty)resolve.getParent()).getName());
      });
    }
  }

  @Test
  public void testNavigateToDefinitionByRefInFileWithIncorrectReference() throws Exception {
    mySchemaFixture.skeleton(new JsonSchemaHeavyTestFixture.Callback() {
      @Override
      public void registerSchemes() {
        mySchemaFixture.addSchema(new UserDefinedJsonSchemaConfiguration("one", JsonSchemaVersion.SCHEMA_4, mySchemaFixture.getUrlUnderTestRoot("/withIncorrectReferenceSchema.json"), false,
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
    return ReadAction.computeBlocking(() -> myFixture.getEditor().getCaretModel().getPrimaryCaret().getOffset());
  }

  private void checkNavigationTo(@NotNull String resolvedText, @NotNull String name, int offset, @NotNull String base, boolean isReference) {
    ReadAction.runBlocking(() -> {
      final PsiElement resolve = isReference
                                 ? myFixture.getFile().findReferenceAt(offset).resolve()
                                 : GotoDeclarationAction.findTargetElement(myFixture.getProject(), myFixture.getEditor(), offset);
      Assertions.assertNotNull(resolve);
      Assertions.assertEquals(resolvedText, resolve.getText());
      final PsiElement parent = resolve.getParent();
      Assertions.assertTrue(parent instanceof JsonProperty);
      Assertions.assertEquals(name, ((JsonProperty)parent).getName());
      Assertions.assertTrue(parent.getParent().getParent() instanceof JsonProperty);
      Assertions.assertEquals(base, ((JsonProperty)parent.getParent().getParent()).getName());
    });
  }

  @Test
  public void testInsideCycledSchemaNavigation() throws Exception {
    mySchemaFixture.skeleton(new JsonSchemaHeavyTestFixture.Callback() {
      @Override
      public void registerSchemes() {
        mySchemaFixture.addSchema(new UserDefinedJsonSchemaConfiguration("one", JsonSchemaVersion.SCHEMA_4,
                                                         mySchemaFixture.getUrlUnderTestRoot("/insideCycledSchemaNavigationSchema.json"),
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

  @Test
  public void testNavigationIntoCycledSchema() throws Exception {
    mySchemaFixture.skeleton(new JsonSchemaHeavyTestFixture.Callback() {
      @Override
      public void registerSchemes() {
        final List<UserDefinedJsonSchemaConfiguration.Item> patterns = Collections.singletonList(
          new UserDefinedJsonSchemaConfiguration.Item("*.json", true, false));
        mySchemaFixture.addSchema(new UserDefinedJsonSchemaConfiguration("one", JsonSchemaVersion.SCHEMA_4, mySchemaFixture.getUrlUnderTestRoot("/cycledSchema.json"), false, patterns));
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

  @Test
  public void testNavigationWithCompositeDefinitionsObject() throws Exception {
    mySchemaFixture.skeleton(new JsonSchemaHeavyTestFixture.Callback() {
      @Override
      public void registerSchemes() {
        final List<UserDefinedJsonSchemaConfiguration.Item> patterns = Collections.singletonList(
          new UserDefinedJsonSchemaConfiguration.Item("*.json", true, false));
        mySchemaFixture.addSchema(new UserDefinedJsonSchemaConfiguration("one", JsonSchemaVersion.SCHEMA_4,
                                                         mySchemaFixture.getUrlUnderTestRoot("/navigationWithCompositeDefinitionsObjectSchema.json"), false, patterns));
      }

      @Override
      public void configureFiles() {
        myFixture.configureByFiles("navigationWithCompositeDefinitionsObjectSchema.json");
      }

      @Override
      public void doCheck() {
        ReadAction.runBlocking(() -> {
          final Collection<JsonStringLiteral> strings = PsiTreeUtil.findChildrenOfType(myFixture.getFile(), JsonStringLiteral.class);
          final List<JsonStringLiteral> list = ContainerUtil.filter(strings, expression -> expression.getText().contains("#/definitions"));
          Assertions.assertEquals(3, list.size());
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
        });
      }
    });
  }

  @Test
  public void testNavigationIntoWithCompositeDefinitionsObject() throws Exception {
    mySchemaFixture.skeleton(new JsonSchemaHeavyTestFixture.Callback() {
      @Override
      public void registerSchemes() {
        final List<UserDefinedJsonSchemaConfiguration.Item> patterns = Collections.singletonList(
          new UserDefinedJsonSchemaConfiguration.Item("*.json", true, false));
        mySchemaFixture.addSchema(new UserDefinedJsonSchemaConfiguration("one", JsonSchemaVersion.SCHEMA_4,
                                                         mySchemaFixture.getUrlUnderTestRoot("/navigationWithCompositeDefinitionsObjectSchema.json"), false, patterns));
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

  @Test
  public void testCompletionWithRootRef() throws Exception {
    mySchemaFixture.skeleton(new JsonSchemaHeavyTestFixture.Callback() {
      @Override
      public void registerSchemes() {
        final List<UserDefinedJsonSchemaConfiguration.Item> patterns = Collections.singletonList(
          new UserDefinedJsonSchemaConfiguration.Item("*.json", true, false));
        mySchemaFixture.addSchema(new UserDefinedJsonSchemaConfiguration("one", JsonSchemaVersion.SCHEMA_4, mySchemaFixture.getUrlUnderTestRoot("/cycledWithRootRefSchema.json"), false, patterns));
      }

      @Override
      public void configureFiles() {
        myFixture.configureByFiles("completionWithRootRef.json", "cycledWithRootRefSchema.json");
      }

      @Override
      public void doCheck() {
        mySchemaFixture.complete();
        checkCompletion("\"id\"", "\"testProp\"");
      }
    });
  }

  @Test
  public void testResolveByValuesCombinations() throws Exception {
    mySchemaFixture.skeleton(new JsonSchemaHeavyTestFixture.Callback() {
      @Override
      public void registerSchemes() {
        final List<UserDefinedJsonSchemaConfiguration.Item> patterns = Collections.singletonList(
          new UserDefinedJsonSchemaConfiguration.Item("*.json", true, false));
        mySchemaFixture.addSchema(new UserDefinedJsonSchemaConfiguration("one", JsonSchemaVersion.SCHEMA_4,
                                                         mySchemaFixture.getUrlUnderTestRoot("/ResolveByValuesCombinationsSchema.json"), false, patterns));
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
            ReadAction.runBlocking(() -> {
              final JsonFile jsonFile = ObjectUtils.tryCast(file, JsonFile.class);
              Assertions.assertNotNull(jsonFile);
              final JsonObject top = ObjectUtils.tryCast(jsonFile.getTopLevelValue(), JsonObject.class);
              Assertions.assertNotNull(top);

              TextRange range = top.findProperty("alive").getNameElement().getTextRange();
              checkNavigationToSchemaVariant("alive", range.getStartOffset() + 1, t.getThird());

              range = top.findProperty("feature").getNameElement().getTextRange();
              checkNavigationToSchemaVariant("feature", range.getStartOffset() + 1, t.getThird());
            });
          }
        );
      }
    });
  }

  private void checkNavigationToSchemaVariant(@NotNull String name, int offset, @NotNull String parentPropertyName) {
    final PsiElement resolve = GotoDeclarationAction.findTargetElement(myFixture.getProject(), myFixture.getEditor(), offset);
    Assertions.assertEquals("\"" + name + "\"", resolve.getText());
    final PsiElement parent = resolve.getParent();
    Assertions.assertTrue(parent instanceof JsonProperty);
    Assertions.assertEquals(name, ((JsonProperty)parent).getName());
    Assertions.assertTrue(parent.getParent().getParent() instanceof JsonProperty);
    final JsonProperty props = (JsonProperty)parent.getParent().getParent();
    Assertions.assertEquals("properties", props.getName());
    final JsonProperty parentProperty = ObjectUtils.tryCast(props.getParent().getParent(), JsonProperty.class);
    Assertions.assertNotNull(parentProperty);
    Assertions.assertEquals(parentPropertyName, parentProperty.getName());
  }
}
