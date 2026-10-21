// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.concurrency.Semaphore;
import com.jetbrains.jsonSchema.JsonSchemaTestServiceImpl;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.JsonSchemaProjectSelfProviderFactory;
import com.jetbrains.jsonSchema.extension.SchemaType;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.inspections.JsonSchemaComplianceInspection;
import com.jetbrains.jsonSchema.impl.light.legacy.JsonSchemaObjectReadingUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static com.jetbrains.jsonSchema.impl.JsonSchemaTraversalUtilsKt.getChildAsText;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class JsonSchemaReadTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  private CodeInsightTestFixture myFixture;

  @BeforeEach
  void setUp() {
    myFixture = codeInsightFixture.get();
    myFixture.setTestDataPath(PlatformTestUtil.getCommunityPath() + "/json/backend/tests/testData/jsonSchema");
  }

  @Test
  public void testReadSchemaItself() throws Exception {
    final File file = new File(PlatformTestUtil.getCommunityPath(), "json/backend/tests/testData/jsonSchema/schema.json");
    final JsonSchemaObject read = getSchemaObject(file);

    assertEquals("http://json-schema.org/draft-04/schema", read.getId());
    assertNotNull(read.getDefinitionByName("positiveInteger"));
    assertNotNull(read.getPropertyByName("multipleOf"));
    assertNotNull(read.getPropertyByName("type"));
    assertNotNull(read.getPropertyByName("additionalProperties"));
    assertEquals(2, read.getPropertyByName("additionalItems").getAnyOf().size());
    assertEquals("#", read.getPropertyByName("additionalItems").getAnyOf().get(1).getRef());

    final JsonSchemaObject required = read.getPropertyByName("required");
    assertEquals("#/definitions/stringArray", required.getRef());

    final JsonSchemaObject minLength = read.getPropertyByName("minLength");
    assertEquals("#/definitions/positiveIntegerDefault0", minLength.getRef());
  }

  @Test
  public void testMainSchemaHighlighting() throws IOException {
    final JsonSchemaService service = JsonSchemaService.Impl.get(myFixture.getProject());
    var versionsToTest = Stream.of(JsonSchemaVersion.SCHEMA_4, JsonSchemaVersion.SCHEMA_6, JsonSchemaVersion.SCHEMA_7).collect(Collectors.toSet());
    final List<JsonSchemaFileProvider> providers = new JsonSchemaProjectSelfProviderFactory().getProviders(myFixture.getProject());
    for (JsonSchemaFileProvider provider: providers) {
      if (!versionsToTest.contains(provider.getSchemaVersion())) continue;

      final VirtualFile mainSchema = provider.getSchemaFile();
      assertNotNull(mainSchema);
      assertTrue(ReadAction.computeBlocking(() -> service.isSchemaFile(mainSchema)));
      // mainSchema could be in a jar
      final VirtualFile copyOfMainSchema =
        myFixture.createFile(mainSchema.getName(), new String(mainSchema.contentsToByteArray(), mainSchema.getCharset()));
      assertNotNull(copyOfMainSchema);
      assertTrue(ReadAction.computeBlocking(() -> service.isSchemaFile(copyOfMainSchema)));

      myFixture.enableInspections(new JsonSchemaComplianceInspection());
      Disposer.register(myFixture.getTestRootDisposable(), new Disposable() {
        @Override
        public void dispose() {
          JsonSchemaTestServiceImpl.setProvider(null);
        }
      });

      myFixture.configureFromExistingVirtualFile(copyOfMainSchema);
      final List<HighlightInfo> infos = myFixture.doHighlighting();
      for (HighlightInfo info : infos) {
        if (!HighlightSeverity.INFORMATION.equals(info.getSeverity())) {
          fail(String.format("%s in: %s", info.getDescription(),
                             myFixture.getEditor().getDocument().getText(TextRange.create(info))));
        }
      }
    }
  }

  private JsonSchemaObject getSchemaObject(File file) throws Exception {
    assertTrue(file.exists());
    final VirtualFile virtualFile = StandardFileSystems.local().refreshAndFindFileByPath(file.getAbsolutePath());
    assertNotNull(virtualFile);
    return ReadAction.computeBlocking(() -> JsonSchemaReader.readFromFile(myFixture.getProject(), virtualFile));
  }

  @Test
  public void testReadSchemaWithCustomTags() throws Exception {
    final File file = new File(PlatformTestUtil.getCommunityPath(), "json/backend/tests/testData/jsonSchema/withNotesCustomTag.json");
    final JsonSchemaObject read = getSchemaObject(file);
    assertNotNull(read.getDefinitionByName("common").getPropertyByName("id"));
  }

  @Test
  public void testArrayItemsSchema() throws Exception {
    final File file = new File(PlatformTestUtil.getCommunityPath(), "json/backend/tests/testData/jsonSchema/arrayItemsSchema.json");
    final JsonSchemaObject read = getSchemaObject(file);
    Iterable<String> iterable = () -> read.getPropertyNames();
    var properties = StreamSupport.stream(iterable.spliterator(), false).toList();
    assertEquals(1, properties.size());
    final JsonSchemaObject object = read.getPropertyByName("color-hex-case");
    var oneOf = object.getOneOf();
    assertEquals(2, oneOf.size());

    final JsonSchemaObject second = oneOf.get(1);
    var list = second.getItemsSchemaList();
    assertEquals(2, list.size());

    final JsonSchemaObject firstItem = list.get(0);
    assertEquals("#/definitions/lowerUpper", firstItem.getRef());
    final JsonSchemaObject definition = JsonSchemaObjectReadingUtils.findRelativeDefinition(read, firstItem.getRef());
    assertNotNull(definition);

    final List<Object> anEnum = definition.getEnum();
    assertEquals(2, anEnum.size());
    assertTrue(anEnum.contains("\"lower\""));
    assertTrue(anEnum.contains("\"upper\""));
  }

  @Test
  public void testReadSchemaWithWrongRequired() throws Exception {
    doTestSchemaReadNotHung(new File(PlatformTestUtil.getCommunityPath(), "json/backend/tests/testData/jsonSchema/WithWrongRequired.json"));
  }

  @Test
  public void testReadSchemaWithWrongItems() throws Exception {
    doTestSchemaReadNotHung(new File(PlatformTestUtil.getCommunityPath(), "json/backend/tests/testData/jsonSchema/WithWrongItems.json"));
  }

  @Test
  public void testReadNestedSchemaObject() {
    var schemaPsi = myFixture.configureByText("testSchemaReading.json", """
      { "foo": {"bar": "hello there"}}
    """);

    ReadAction.runBlocking(() -> {
      var root = new JsonSchemaReader().read(schemaPsi);
      var existingNodeText = getChildAsText(root, "foo", "bar");
      assertEquals("hello there", existingNodeText);

      var missingNodeText = getChildAsText(root, "foo", "buz");
      assertNull(missingNodeText);

      var nonTextualNode = getChildAsText(root);
      assertNull(nonTextualNode);
    });
  }

  @Test
  public void testSingleSchemaUsesFirstAvailableProviderWhenNoUserSchema() {
    VirtualFile targetFile = myFixture.addFileToProject("target.json", "{}").getVirtualFile();
    VirtualFile firstSchema = myFixture.addFileToProject("json_schema_test/first.json", "{\"type\":\"object\"}").getVirtualFile();
    VirtualFile secondSchema = myFixture.addFileToProject("json_schema_test/second.json", "{\"type\":\"object\"}").getVirtualFile();
    JsonSchemaServiceImpl service = new JsonSchemaTestServiceImpl(myFixture.getProject());

    JsonSchemaTestServiceImpl.setProviders(new NonUserSchemaProvider(firstSchema), new NonUserSchemaProvider(secondSchema));
    Disposer.register(myFixture.getTestRootDisposable(), () -> JsonSchemaTestServiceImpl.setProvider(null));
    ServiceContainerUtil.replaceService(myFixture.getProject(), JsonSchemaService.class, service, myFixture.getTestRootDisposable());

    assertEquals(List.of(firstSchema), ReadAction.computeBlocking(() -> List.copyOf(service.getSchemasForFile(targetFile, true, false))));
  }

  private void doTestSchemaReadNotHung(final File file) throws Exception {
    // because of threading
    if (Runtime.getRuntime().availableProcessors() < 2) return;

    assertTrue(file.exists());

    final AtomicBoolean done = new AtomicBoolean();
    final AtomicReference<Exception> error = new AtomicReference<>();
    final Semaphore semaphore = new Semaphore();
    semaphore.down();
    Future<?> thread = ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        ReadAction.run(() -> getSchemaObject(file));
        done.set(true);
      }
      catch (Exception e) {
        error.set(e);
      }
      finally {
        semaphore.up();
      }
    });
    try {
      semaphore.waitFor(TimeUnit.SECONDS.toMillis(120));
      if (error.get() != null) throw error.get();
      assertTrue(done.get(), "Reading test schema hung!");
    }
    finally {
      thread.get();
    }
  }

  private static final class NonUserSchemaProvider implements JsonSchemaFileProvider {
    private final @NotNull VirtualFile mySchemaFile;

    private NonUserSchemaProvider(@NotNull VirtualFile schemaFile) {
      mySchemaFile = schemaFile;
    }

    @Override
    public boolean isAvailable(@NotNull VirtualFile file) {
      return "target.json".equals(file.getName());
    }

    @Override
    public @NotNull String getName() {
      return mySchemaFile.getNameWithoutExtension();
    }

    @Override
    public @NotNull VirtualFile getSchemaFile() {
      return mySchemaFile;
    }

    @Override
    public @NotNull SchemaType getSchemaType() {
      return SchemaType.schema;
    }
  }
}
