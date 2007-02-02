package com.intellij.localvcs;

import static org.easymock.EasyMock.*;
import org.junit.Test;

public class CachingContentStorageTest extends LocalVcsTestCase {
  IContentStorage subject = createMock(IContentStorage.class);
  IContentStorage s = new CachingContentStorage(subject);

  @Test
  public void testCachingOnStore() throws Exception {
    byte[] c = b("content");

    expect(subject.store(c)).andReturn(3).times(1);
    replay(subject);

    assertEquals(3, s.store(c));
    assertEquals(c, s.load(3));

    verify(subject);
  }

  @Test
  public void testCachingOnLoad() throws Exception {
    expect(subject.load(2)).andReturn(b("content")).times(1);
    replay(subject);

    assertEquals(b("content"), s.load(2));
    assertEquals(b("content"), s.load(2));

    verify(subject);
  }

  @Test
  public void testRemovingFromCache() throws Exception {
    expect(subject.load(1)).andReturn(b("content")).times(2);
    subject.remove(1);
    replay(subject);

    assertEquals(b("content"), s.load(1));
    s.remove(1);
    assertEquals(b("content"), s.load(1));

    verify(subject);
  }

  @Test
  public void testSaveAndClose() throws Exception {
    subject.save();
    subject.close();
    replay(subject);

    s.save();
    s.close();

    verify(subject);
  }
}
