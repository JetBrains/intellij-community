package com.intellij.history.core.storage;

import com.intellij.history.core.LocalVcsTestCase;
import static org.easymock.EasyMock.*;
import org.junit.Test;

import java.io.IOException;

public class CachingContentStorageTest extends LocalVcsTestCase {
  IContentStorage subject = createMock(IContentStorage.class);
  IContentStorage s = new CachingContentStorage(subject);

  @Test
  public void testCachingOnStore() throws Exception {
    byte[] c = "content".getBytes();

    expect(subject.store(c)).andReturn(3).times(1);
    replay(subject);

    assertEquals(3, s.store(c));
    assertEquals(c, s.load(3));

    verify(subject);
  }

  @Test
  public void testCachingOnLoad() throws Exception {
    expect(subject.load(2)).andReturn("content".getBytes()).times(1);
    replay(subject);

    assertEquals("content".getBytes(), s.load(2));
    assertEquals("content".getBytes(), s.load(2));

    verify(subject);
  }

  @Test
  public void testRemovingFromCache() throws Exception {
    expect(subject.load(1)).andReturn("content".getBytes()).times(2);
    subject.remove(1);
    replay(subject);

    assertEquals("content".getBytes(), s.load(1));
    s.remove(1);
    assertEquals("content".getBytes(), s.load(1));

    verify(subject);
  }

  @Test
  public void testDoesNotCacheBigContent() throws IOException {
    byte[] b = new byte[CachingContentStorage.MAX_CACHED_CONTENT_LENGTH + 1];
    expect(subject.load(1)).andReturn(b).times(2);
    replay(subject);

    s.load(1);
    s.load(1);

    verify(subject);
  }

  @Test
  public void testSaveAndClose() {
    subject.save();
    subject.close();
    replay(subject);

    s.save();
    s.close();

    verify(subject);
  }
}
