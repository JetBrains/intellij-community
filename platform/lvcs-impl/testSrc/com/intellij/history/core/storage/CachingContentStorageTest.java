/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.history.core.storage;

import com.intellij.history.core.LocalVcsTestCase;
import static org.easymock.EasyMock.*;
import org.junit.Test;

public class CachingContentStorageTest extends LocalVcsTestCase {
  IContentStorage subject = createMock(IContentStorage.class);
  IContentStorage s = new CachingContentStorage(subject);

  @Test
  public void testCachingOnStore() throws Exception {
    byte[] c = "content".getBytes();

    expect(subject.store(c)).andReturn(3).times(1);
    replay(subject);

    assertEquals(3, s.store(c));
    assertArrayEquals(c, s.load(3));

    verify(subject);
  }

  @Test
  public void testCachingOnLoad() throws Exception {
    expect(subject.load(2)).andReturn("content".getBytes()).times(1);
    replay(subject);

    assertArrayEquals("content".getBytes(), s.load(2));
    assertArrayEquals("content".getBytes(), s.load(2));

    verify(subject);
  }

  @Test
  public void testRemovingFromCache() throws Exception {
    expect(subject.load(1)).andReturn("content".getBytes()).times(2);
    subject.remove(1);
    replay(subject);

    assertArrayEquals("content".getBytes(), s.load(1));
    s.remove(1);
    assertArrayEquals("content".getBytes(), s.load(1));

    verify(subject);
  }

  @Test
  public void testDoesNotCacheBigContent() throws Exception {
    byte[] b = new byte[CachingContentStorage.MAX_CACHED_CONTENT_LENGTH + 1];
    expect(subject.load(1)).andReturn(b).times(2);
    replay(subject);

    s.load(1);
    s.load(1);

    verify(subject);
  }
}
