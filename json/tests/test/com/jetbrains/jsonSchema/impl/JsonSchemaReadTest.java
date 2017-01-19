package com.jetbrains.jsonSchema.impl;

import com.intellij.codeInsight.completion.CompletionTestCase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.json.JsonLanguage;
import com.intellij.lang.LanguageAnnotators;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.concurrency.Semaphore;
import com.jetbrains.jsonSchema.JsonSchemaFileType;
import com.jetbrains.jsonSchema.JsonSchemaTestServiceImpl;
import com.jetbrains.jsonSchema.ide.JsonSchemaAnnotator;
import org.junit.Assert;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Irina.Chernushina on 8/29/2015.
 */
public class JsonSchemaReadTest extends CompletionTestCase {
  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath() + "/json/tests/testData/jsonSchema";
  }

  public void testReadSchemaItself() throws Exception {
    final File file = new File(PlatformTestUtil.getCommunityPath(), "json/tests/testData/jsonSchema/schema.json");
    final JsonSchemaObject read = getSchemaObject(file);

    Assert.assertEquals("http://json-schema.org/draft-04/schema#", read.getId());
    Assert.assertTrue(read.getDefinitions().containsKey("positiveInteger"));
    Assert.assertTrue(read.getProperties().containsKey("multipleOf"));
    Assert.assertTrue(read.getProperties().containsKey("type"));
    Assert.assertTrue(read.getProperties().containsKey("additionalProperties"));
    Assert.assertEquals(2, read.getProperties().get("additionalItems").getAnyOf().size());
    Assert.assertEquals("#", read.getProperties().get("additionalItems").getAnyOf().get(1).getRef());

    final JsonSchemaObject required = read.getProperties().get("required");
    Assert.assertEquals(JsonSchemaType._array, required.getType());
    Assert.assertEquals(1, required.getMinItems().intValue());
    Assert.assertEquals(JsonSchemaType._string, required.getItemsSchema().getType());

    final JsonSchemaObject minLength = read.getProperties().get("minLength");
    Assert.assertNotNull(minLength.getAllOf());
    final List<JsonSchemaObject> minLengthAllOf = minLength.getAllOf();
    boolean haveIntegerType = false;
    Integer defaultValue = null;
    Integer minValue = null;
    for (JsonSchemaObject object : minLengthAllOf) {
      haveIntegerType |= JsonSchemaType._integer.equals(object.getType());
      if (object.getDefault() instanceof  Number) {
        defaultValue = ((Number)object.getDefault()).intValue();
      }
      if (object.getMinimum() != null) {
        minValue = object.getMinimum().intValue();
      }
    }
    Assert.assertTrue(haveIntegerType);
    Assert.assertEquals(0, defaultValue.intValue());
    Assert.assertEquals(0, minValue.intValue());
  }

  public void testMainSchemaHighlighting() throws Exception {
    final Set<VirtualFile> files = JsonSchemaServiceEx.Impl.getEx(myProject).getSchemaFiles();
    VirtualFile mainSchema = null;
    for (VirtualFile file : files) {
      if ("schema.json".equals(file.getName())) {
        mainSchema = file;
        break;
      }
    }
    assertNotNull(mainSchema);
    assertTrue(JsonSchemaFileType.INSTANCE.equals(mainSchema.getFileType()));

    final Annotator annotator = new JsonSchemaAnnotator();
    LanguageAnnotators.INSTANCE.addExplicitExtension(JsonLanguage.INSTANCE, annotator);
    Disposer.register(getTestRootDisposable(), new Disposable() {
      @Override
      public void dispose() {
        LanguageAnnotators.INSTANCE.removeExplicitExtension(JsonLanguage.INSTANCE, annotator);
        JsonSchemaTestServiceImpl.setProvider(null);
      }
    });

    configureByExistingFile(mainSchema);
    final List<HighlightInfo> infos = doHighlighting();
    for (HighlightInfo info : infos) {
      if (!HighlightSeverity.INFORMATION.equals(info.getSeverity())) assertFalse(info.getDescription(), true);
    }
  }

  private JsonSchemaObject getSchemaObject(File file) throws IOException {
    Assert.assertTrue(file.exists());
    final VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
    Assert.assertNotNull(virtualFile);
    final JsonSchemaReader reader = JsonSchemaReader.create(myProject, virtualFile);
    return reader.read();
  }

  public void testReadSchemaWithCustomTags() throws Exception {
    final File file = new File(PlatformTestUtil.getCommunityPath(), "json/tests/testData/jsonSchema/withNotesCustomTag.json");
    final JsonSchemaObject read = getSchemaObject(file);
    Assert.assertTrue(read.getDefinitions().get("common").getProperties().containsKey("id"));
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
    final List<Object> anEnum = firstItem.getEnum();
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

  private void doTestSchemaReadNotHung(final File file) throws IOException {
    // because of threading
    if (Runtime.getRuntime().availableProcessors() < 2) return;

    Assert.assertTrue(file.exists());

    final AtomicBoolean done = new AtomicBoolean();
    final AtomicReference<IOException> error = new AtomicReference<>();
    final Semaphore semaphore = new Semaphore();
    semaphore.down();
    final Thread thread = new Thread(() -> {
      try {
        ApplicationManager.getApplication().runReadAction((ThrowableComputable<JsonSchemaObject, IOException>)() -> getSchemaObject(file));
        done.set(true);
      }
      catch (IOException e) {
        error.set(e);
      }
      finally {
        semaphore.up();
      }
    }, getClass().getName() + ": read test json schema " + file.getName());
    thread.setDaemon(true);
    try {
      thread.start();
      semaphore.waitFor(TimeUnit.SECONDS.toMillis(120));
      if (error.get() != null) throw error.get();
      Assert.assertTrue("Reading test schema hung!", done.get());
    } finally {
      thread.interrupt();
    }
  }
}
