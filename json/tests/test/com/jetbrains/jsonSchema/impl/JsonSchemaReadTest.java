package com.jetbrains.jsonSchema.impl;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.concurrency.Semaphore;
import com.jetbrains.jsonSchema.JsonSchemaTestServiceImpl;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.JsonSchemaProjectSelfProviderFactory;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.inspections.JsonSchemaComplianceInspection;
import com.jetbrains.jsonSchema.impl.light.legacy.JsonSchemaObjectReadingUtils;
import com.jetbrains.jsonSchema.impl.light.nodes.JsonSchemaReader2;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.io.File;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.jetbrains.jsonSchema.impl.JsonSchemaTraversalUtilsKt.getChildAsText;

public class JsonSchemaReadTest extends BasePlatformTestCase {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath() + "/json/tests/testData/jsonSchema";
  }

  public void testReadSchemaItself() throws Exception {
    final File file = new File(PlatformTestUtil.getCommunityPath(), "json/tests/testData/jsonSchema/schema.json");
    final JsonSchemaObject read = getSchemaObject(file);

    Assert.assertEquals("http://json-schema.org/draft-04/schema", read.getId());
    Assert.assertNotNull(read.getDefinitionByName("positiveInteger"));
    Assert.assertNotNull(read.getPropertyByName("multipleOf"));
    Assert.assertNotNull(read.getPropertyByName("type"));
    Assert.assertNotNull(read.getPropertyByName("additionalProperties"));
    Assert.assertEquals(2, read.getPropertyByName("additionalItems").getAnyOf().size());
    Assert.assertEquals("#", read.getPropertyByName("additionalItems").getAnyOf().get(1).getRef());

    final JsonSchemaObject required = read.getPropertyByName("required");
    Assert.assertEquals("#/definitions/stringArray", required.getRef());

    final JsonSchemaObject minLength = read.getPropertyByName("minLength");
    Assert.assertEquals("#/definitions/positiveIntegerDefault0", minLength.getRef());
  }

  public void testMainSchemaHighlighting() {
    final JsonSchemaService service = JsonSchemaService.Impl.get(getProject());
    var versionsToTest = Stream.of(JsonSchemaVersion.SCHEMA_4, JsonSchemaVersion.SCHEMA_6, JsonSchemaVersion.SCHEMA_7).collect(Collectors.toSet());
    final List<JsonSchemaFileProvider> providers = new JsonSchemaProjectSelfProviderFactory().getProviders(getProject());
    for (JsonSchemaFileProvider provider: providers) {
      if (!versionsToTest.contains(provider.getSchemaVersion())) continue;

      final VirtualFile mainSchema = provider.getSchemaFile();
      assertNotNull(mainSchema);
      assertTrue(service.isSchemaFile(mainSchema));

      myFixture.enableInspections(new JsonSchemaComplianceInspection());
      Disposer.register(getTestRootDisposable(), new Disposable() {
        @Override
        public void dispose() {
          JsonSchemaTestServiceImpl.setProvider(null);
        }
      });

      myFixture.configureFromExistingVirtualFile(mainSchema);
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
    Assert.assertTrue(file.exists());
    final VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
    Assert.assertNotNull(virtualFile);
    return JsonSchemaReader.readFromFile(getProject(), virtualFile);
  }

  public void testReadSchemaWithCustomTags() throws Exception {
    final File file = new File(PlatformTestUtil.getCommunityPath(), "json/tests/testData/jsonSchema/withNotesCustomTag.json");
    final JsonSchemaObject read = getSchemaObject(file);
    Assert.assertNotNull(read.getDefinitionByName("common").getPropertyByName("id"));
  }

  public void testArrayItemsSchema() throws Exception {
    final File file = new File(PlatformTestUtil.getCommunityPath(), "json/tests/testData/jsonSchema/arrayItemsSchema.json");
    final JsonSchemaObject read = getSchemaObject(file);
    Iterable<String> iterable = () -> read.getPropertyNames();
    var properties = StreamSupport.stream(iterable.spliterator(), false).toList();
    Assert.assertEquals(1, properties.size());
    final JsonSchemaObject object = read.getPropertyByName("color-hex-case");
    var oneOf = object.getOneOf();
    Assert.assertEquals(2, oneOf.size());

    final JsonSchemaObject second = oneOf.get(1);
    var list = second.getItemsSchemaList();
    Assert.assertEquals(2, list.size());

    final JsonSchemaObject firstItem = list.get(0);
    Assert.assertEquals("#/definitions/lowerUpper", firstItem.getRef());
    final JsonSchemaObject definition = JsonSchemaObjectReadingUtils.findRelativeDefinition(read, firstItem.getRef());
    Assert.assertNotNull(definition);

    final List<Object> anEnum = definition.getEnum();
    Assert.assertEquals(2, anEnum.size());
    Assert.assertTrue(anEnum.contains("\"lower\""));
    Assert.assertTrue(anEnum.contains("\"upper\""));
  }

  public void testReadSchemaWithWrongRequired() throws Exception {
    doTestSchemaReadNotHung(new File(PlatformTestUtil.getCommunityPath(), "json/tests/testData/jsonSchema/WithWrongRequired.json"));
  }

  public void testReadSchemaWithWrongItems() throws Exception {
    doTestSchemaReadNotHung(new File(PlatformTestUtil.getCommunityPath(), "json/tests/testData/jsonSchema/WithWrongItems.json"));
  }

  public void testReadNestedSchemaObject() {
    var schemaPsi = myFixture.configureByText("testSchemaReading.json", """
      { "foo": {"bar": "hello there"}}
    """);

    var root = new JsonSchemaReader(schemaPsi.getVirtualFile()).read(schemaPsi);
    var existingNodeText = getChildAsText(root, "foo", "bar");
    Assert.assertEquals("hello there", existingNodeText);

    var missingNodeText = getChildAsText(root, "foo", "buz");
    Assert.assertNull(missingNodeText);

    var nonTextualNode = getChildAsText(root);
    Assert.assertNull(nonTextualNode);
  }

  private void doTestSchemaReadNotHung(final File file) throws Exception {
    // because of threading
    if (Runtime.getRuntime().availableProcessors() < 2) return;

    Assert.assertTrue(file.exists());

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
      Assert.assertTrue("Reading test schema hung!", done.get());
    }
    finally {
      thread.get();
    }
  }
}
