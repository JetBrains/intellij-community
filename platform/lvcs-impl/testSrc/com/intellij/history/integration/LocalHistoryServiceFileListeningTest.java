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

package com.intellij.history.integration;

import com.intellij.history.core.tree.Entry;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.Test;

public class LocalHistoryServiceFileListeningTest extends LocalHistoryServiceTestCase {
  @Test
  public void testListening() {
    VirtualFile f = new TestVirtualFile("file", "content", 123L);
    fileManager.fireFileCreated(f);

    Entry e = vcs.findEntry("file");
    assertNotNull(e);

    assertFalse(e.isDirectory());

    assertEquals(c("content"), e.getContent());
    assertEquals(123L, e.getTimestamp());
  }

  @Test
  public void testUnsubscribingRefreshUpdatersOnShutdown() {
    assertTrue(fileManager.hasRefreshUpdater());
    service.shutdown();
    assertFalse(fileManager.hasRefreshUpdater());
  }

  @Test
  public void testUnsubscribingFromFileManagerRefreshEventsOnShutdown() {
    assertTrue(fileManager.hasVirtualFileManagerListener());
    service.shutdown();
    assertFalse(fileManager.hasVirtualFileManagerListener());
  }

  @Test
  public void testUnsubscribingFromFileManagerOnShutdown() {
    assertTrue(fileManager.hasVirtualFileListener());
    service.shutdown();
    assertFalse(fileManager.hasVirtualFileListener());

    VirtualFile f = new TestVirtualFile("file", null, -1);
    fileManager.fireFileCreated(f);

    assertFalse(vcs.hasEntry("file"));
  }
}
