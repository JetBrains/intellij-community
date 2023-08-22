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
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class JsonSchemaReadTest extends BasePlatformTestCase {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath() + "/json/tests/testData/jsonSchema";
  }

  public void testReadSchemaItself() throws Exception {
    final File file = new File(PlatformTestUtil.getCommunityPath(), "json/tests/testData/jsonSchema/schema.json");
    final JsonSchemaObject read = getSchemaObject(file);

    Assert.assertEquals("http://json-schema.org/draft-04/schema#", read.getId());
    Assert.assertTrue(read.getDefinitionsMap().containsKey("positiveInteger"));
    Assert.assertTrue(read.getProperties().containsKey("multipleOf"));
    Assert.assertTrue(read.getProperties().containsKey("type"));
    Assert.assertTrue(read.getProperties().containsKey("additionalProperties"));
    Assert.assertEquals(2, read.getProperties().get("additionalItems").getAnyOf().size());
    Assert.assertEquals("#", read.getProperties().get("additionalItems").getAnyOf().get(1).getRef());

    final JsonSchemaObject required = read.getProperties().get("required");
    Assert.assertEquals("#/definitions/stringArray", required.getRef());

    final JsonSchemaObject minLength = read.getProperties().get("minLength");
    Assert.assertEquals("#/definitions/positiveIntegerDefault0", minLength.getRef());
  }

  public void testMainSchemaHighlighting() {
    final JsonSchemaService service = JsonSchemaService.Impl.get(getProject());
    final List<JsonSchemaFileProvider> providers = new JsonSchemaProjectSelfProviderFactory().getProviders(getProject());
    Assert.assertEquals(JsonSchemaProjectSelfProviderFactory.TOTAL_PROVIDERS, providers.size());
    for (JsonSchemaFileProvider provider: providers) {
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
    Assert.assertTrue(read.getDefinitionsMap().get("common").getProperties().containsKey("id"));
  }

  public void testArrayItemsSchema() throws Exception {
    final File file = new File(PlatformTestUtil.getCommunityPath(), "json/tests/testData/jsonSchema/arrayItemsSchema.json");
    final JsonSchemaObject read = getSchemaObject(file);
    final Map<String, JsonSchemaObject> properties = read.getProperties();
    Assert.assertEquals(1, properties.size());
    final JsonSchemaObject object = properties.get("color-hex-case");
    final List<JsonSchemaObject> oneOf = object.getOneOf();
    Assert.assertEquals(2, oneOf.size());

    final JsonSchemaObject second = oneOf.get(1);
    final List<JsonSchemaObject> list = second.getItemsSchemaList();
    Assert.assertEquals(2, list.size());

    final JsonSchemaObject firstItem = list.get(0);
    Assert.assertEquals("#/definitions/lowerUpper", firstItem.getRef());
    final JsonSchemaObject definition = read.findRelativeDefinition(firstItem.getRef());
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
