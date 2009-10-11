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

import com.intellij.history.core.revisions.Revision;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class LocalHistoryServiceRootsOnStartupTest extends LocalHistoryServiceTestCase {
  // todo what about roots in jars (non-local file system)?

  @Before
  public void setUp() {
    initWithoutStartup(createLocalVcs());
  }

  @Test
  public void testUpdatingRootsOnStartup() {
    roots.add(new TestVirtualFile("c:/root"));
    startupService();

    assertTrue(vcs.hasEntry("c:/root"));
  }

  @Test
  public void testPuttingLabelAfterUpdate() {
    roots.add(new TestVirtualFile("c:/root"));

    configuration.ADD_LABEL_ON_PROJECT_OPEN = true;
    startupService();

    List<Revision> rr = vcs.getRevisionsFor("c:/root");
    assertEquals(2, rr.size());
    assertEquals("Project open", rr.get(0).getName());
  }

  @Test
  public void testDoesNotPutLabelIfLabelingIsDisabled() {
    roots.add(new TestVirtualFile("c:/root"));

    configuration.ADD_LABEL_ON_PROJECT_OPEN = false;
    startupService();

    List<Revision> rr = vcs.getRevisionsFor("c:/root");
    assertEquals(1, rr.size());
  }

  @Test
  public void testAddingNewFiles() {
    TestVirtualFile root = new TestVirtualFile("c:/root");
    root.addChild(new TestVirtualFile("file", "", -1));
    roots.add(root);

    startupService();

    assertTrue(vcs.hasEntry("c:/root/file"));
  }

  @Test
  public void testUpdatingOutdatedFiles() {
    vcs.createDirectory("c:/root");
    vcs.createFile("c:/root/file", cf("old"), 111L, false);

    TestVirtualFile root = new TestVirtualFile("c:/root");
    root.addChild(new TestVirtualFile("file", "new", 222L));
    roots.add(root);

    startupService();

    assertEquals(c("new"), vcs.getEntry("c:/root/file").getContent());
  }

  @Test
  public void testDeleteObsoleteFiles() {
    vcs.createDirectory("c:/root");
    long timestamp = -1;
    vcs.createFile("c:/root/file", null, timestamp, false);

    roots.add(new TestVirtualFile("c:/root"));
    startupService();

    assertFalse(vcs.hasEntry("c:/root/file"));
  }

  @Test
  public void testDoesNotUpdateRootsBeforeStartupActivity() {
    roots.add(new TestVirtualFile("c:/root"));
    initWithoutStartup(createLocalVcs());

    assertFalse(vcs.hasEntry("c:/root"));
  }

  @Test
  public void testUnsubscribingFromRootChangesOnShutdown() {
    initAndStartup(createLocalVcs());
    service.shutdown();

    roots.add(new TestVirtualFile("root"));
    rootManager.updateRoots();

    assertFalse(vcs.hasEntry("root"));
  }
}
