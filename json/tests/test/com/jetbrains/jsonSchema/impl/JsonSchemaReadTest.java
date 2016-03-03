package com.jetbrains.jsonSchema.impl;

import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.concurrency.Semaphore;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Irina.Chernushina on 8/29/2015.
 */
public class JsonSchemaReadTest {
  @org.junit.Test
  public void testReadSchemaItself() throws Exception {
    final File file = new File(PlatformTestUtil.getCommunityPath(), "json/tests/testData/jsonSchema/schema.json");
    Assert.assertTrue(file.exists());
    final JsonSchemaReader reader = new JsonSchemaReader();
    final JsonSchemaObject read = reader.read(new FileReader(file));

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

  @Test
  public void testReadSchemaWithCustomTags() throws Exception {
    final File file = new File(PlatformTestUtil.getCommunityPath(), "json/tests/testData/jsonSchema/withNotesCustomTag.json");
    Assert.assertTrue(file.exists());
    final JsonSchemaReader reader = new JsonSchemaReader();
    final JsonSchemaObject read = reader.read(new FileReader(file));
    Assert.assertTrue(read.getDefinitions().get("common").getProperties().containsKey("id"));
  }

  @Test
  public void testReadSchemaWithWrongRequired() throws Exception {
    testSchemaReadNotHung(new File(PlatformTestUtil.getCommunityPath(), "json/tests/testData/jsonSchema/WithWrongRequired.json"));
  }

  @Test
  public void testReadSchemaWithWrongItems() throws Exception {
    testSchemaReadNotHung(new File(PlatformTestUtil.getCommunityPath(), "json/tests/testData/jsonSchema/WithWrongItems.json"));
  }

  private void testSchemaReadNotHung(final File file) throws IOException {
    // because of threading
    if (Runtime.getRuntime().availableProcessors() < 2) return;

    Assert.assertTrue(file.exists());

    final AtomicBoolean done = new AtomicBoolean();
    final AtomicReference<IOException> error = new AtomicReference<>();
    final Semaphore semaphore = new Semaphore();
    semaphore.down();
    final Thread thread = new Thread(new Runnable() {
      @Override
      public void run() {
        final JsonSchemaReader reader = new JsonSchemaReader();
        try {
          reader.read(new FileReader(file));
          done.set(true);
        }
        catch (IOException e) {
          error.set(e);
        }
        finally {
          semaphore.up();
        }
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
