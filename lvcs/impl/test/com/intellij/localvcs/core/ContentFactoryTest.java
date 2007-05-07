package com.intellij.localvcs.core;

import com.intellij.localvcs.core.storage.Content;
import org.junit.Test;

import java.io.IOException;

public class ContentFactoryTest extends LocalVcsTestCase {
  TestStorage s = new TestStorage();

  @Test
  public void testContentCreation() {
    ContentFactory f = new MyContentFactory(new byte[]{123});

    Content c = f.createContent(s);
    assertEquals(new byte[]{123}, c.getBytes());
  }

  @Test
  public void testReturningUnavailableContentIfTooLong() {
    ContentFactory f = new MyContentFactory(new byte[ContentFactory.MAX_CONTENT_LENGTH + 1]);

    Content c = f.createContent(s);
    assertFalse(c.isAvailable());
  }

  @Test
  public void testDoesNotAskForContentIfTooLong() {
    final boolean[] isCalled = new boolean[1];

    ContentFactory f = new ContentFactory() {
      @Override
      public byte[] getBytes() {
        isCalled[0] = true;
        return null;
      }

      public long getLength() {
        return ContentFactory.MAX_CONTENT_LENGTH + 1;
      }
    };

    f.createContent(s);
    assertFalse(isCalled[0]);
  }

  @Test
  public void testReturningUnavailableContentIfIOExceptionOccursOnGettingLength() {
    ContentFactory f = new MyContentFactory(new byte[1]) {
      @Override
      public long getLength() throws IOException {
        throw new IOException();
      }
    };

    Content c = f.createContent(s);
    assertFalse(c.isAvailable());
  }

  @Test
  public void testReturningUnavailableContentIfIOExceptionOccursOnGettingBytes() {
    ContentFactory f = new MyContentFactory(new byte[1]) {
      @Override
      public byte[] getBytes() throws IOException {
        throw new IOException();
      }
    };

    Content c = f.createContent(s);
    assertFalse(c.isAvailable());
  }

  class MyContentFactory extends ContentFactory {
    private byte[] myBytes;

    public MyContentFactory(byte[] bytes) {
      myBytes = bytes;
    }

    @Override
    public byte[] getBytes() throws IOException {
      return myBytes;
    }

    @Override
    public long getLength() throws IOException {
      return myBytes.length;
    }
  }
}
