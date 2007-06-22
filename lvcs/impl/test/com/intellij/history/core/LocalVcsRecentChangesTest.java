package com.intellij.history.core;

import com.intellij.history.core.revisions.RecentChange;
import com.intellij.history.core.tree.Entry;
import org.junit.Test;

import java.util.List;

public class LocalVcsRecentChangesTest extends LocalVcsTestCase {
  LocalVcs vcs = new InMemoryLocalVcs();

  @Test
  public void testRecentChanges() {
    vcs.beginChangeSet();
    vcs.createFile("f1", null, -1);
    vcs.endChangeSet("a");

    vcs.beginChangeSet();
    vcs.createFile("f2", null, -1);
    vcs.endChangeSet("b");

    List<RecentChange> cc = vcs.getRecentChanges();
    assertRecentChanges(cc, "b", "a");

    RecentChange c0 = cc.get(0);
    RecentChange c1 = cc.get(1);

    assertNotNull(findEntryAfter(c0, "f1"));
    assertNotNull(findEntryAfter(c0, "f2"));
    assertNotNull(findEntryBefore(c0, "f1"));
    assertNull(findEntryBefore(c0, "f2"));

    assertNotNull(findEntryAfter(c1, "f1"));
    assertNull(findEntryAfter(c1, "f2"));
    assertNull(findEntryBefore(c1, "f1"));
    assertNull(findEntryBefore(c1, "f2"));
  }

  @Test
  public void testDoesNotIncludeUnnamedChanges() {
    vcs.beginChangeSet();
    vcs.createFile("f1", null, -1);
    vcs.endChangeSet("a");

    vcs.createFile("f2", null, -1);

    vcs.beginChangeSet();
    vcs.createFile("f3", null, -1);
    vcs.endChangeSet("b");

    List<RecentChange> cc = vcs.getRecentChanges();
    assertRecentChanges(cc, "b", "a");

    RecentChange c0 = cc.get(0);
    RecentChange c1 = cc.get(1);

    assertNotNull(findEntryAfter(c0, "f1"));
    assertNotNull(findEntryAfter(c0, "f2"));
    assertNotNull(findEntryAfter(c0, "f3"));
    assertNotNull(findEntryBefore(c0, "f1"));
    assertNotNull(findEntryBefore(c0, "f2"));
    assertNull(findEntryBefore(c0, "f3"));

    assertNotNull(findEntryAfter(c1, "f1"));
    assertNull(findEntryAfter(c1, "f2"));
    assertNull(findEntryAfter(c1, "f3"));
    assertNull(findEntryBefore(c1, "f1"));
    assertNull(findEntryBefore(c1, "f2"));
    assertNull(findEntryBefore(c1, "f3"));
  }

  @Test
  public void testDoesNotIncludeLocalFileChanges() {
    vcs.beginChangeSet();
    vcs.createFile("f1", null, -1);
    vcs.endChangeSet("a");

    vcs.beginChangeSet();
    vcs.createFile("f2", null, -1);
    vcs.changeFileContent("f1", null, -1);
    vcs.endChangeSet("b");

    vcs.beginChangeSet();
    vcs.changeFileContent("f2", null, -1);
    vcs.endChangeSet("c");

    List<RecentChange> cc = vcs.getRecentChanges();
    assertEquals(2, cc.size());
    assertEquals("b", cc.get(0).getChangeName());
    assertEquals("a", cc.get(1).getChangeName());
  }

  @Test
  public void testDoesNotIncludeLabels() {
    vcs.beginChangeSet();
    vcs.createFile("f", null, -1);
    vcs.endChangeSet("change");
    vcs.putLabel("label");

    List<RecentChange> cc = vcs.getRecentChanges();
    assertEquals(1, cc.size());
    assertEquals("change", cc.get(0).getChangeName());
  }

  @Test
  public void testRecentChangesForSeveralRoots() {
    vcs.beginChangeSet();
    vcs.createDirectory("root/dir");
    vcs.createDirectory("anotherRoot/anotherDir");
    vcs.endChangeSet("a");

    vcs.beginChangeSet();
    vcs.createFile("root/dir/f1", null, -1);
    vcs.endChangeSet("b");

    vcs.beginChangeSet();
    vcs.createFile("anotherRoot/anotherDir/f2", null, -1);
    vcs.endChangeSet("c");

    List<RecentChange> cc = vcs.getRecentChanges();
    assertRecentChanges(cc, "c", "b", "a");

    RecentChange c0 = cc.get(0);
    RecentChange c1 = cc.get(1);

    assertNotNull(findEntryAfter(c0, "root/dir/f1"));
    assertNotNull(findEntryAfter(c0, "anotherRoot/anotherDir/f2"));
    assertNotNull(findEntryBefore(c0, "root/dir/f1"));
    assertNull(findEntryBefore(c0, "anotherRoot/anotherDir/f2"));

    assertNotNull(findEntryAfter(c1, "root/dir/f1"));
    assertNull(findEntryAfter(c1, "anotherRoot/anotherDir/f2"));
    assertNull(findEntryBefore(c1, "root/dir/f1"));
    assertNull(findEntryBefore(c1, "anotherRoot/anotherDir/f2"));
  }

  @Test
  public void testIncludeOnlyLastValuable20Changes() {
    for (int i = 0; i < 40; i++) {
      vcs.beginChangeSet();
      vcs.createFile("f" + String.valueOf(i), null, -1);
      vcs.endChangeSet(String.valueOf(i));

      vcs.beginChangeSet();
      vcs.changeFileContent("f" + String.valueOf(i), null, -1);
      vcs.endChangeSet(String.valueOf(i) + "_");
    }

    List<RecentChange> cc = vcs.getRecentChanges();
    assertEquals(20, cc.size());

    for (int i = 0; i < 20; i++) {
      assertEquals(String.valueOf(39 - i), cc.get(i).getChangeName());
    }
  }

  private void assertRecentChanges(List<RecentChange> cc, String... names) {
    assertEquals(names.length, cc.size());
    for (int i = 0; i < names.length; i++) {
      assertEquals(names[i], cc.get(i).getChangeName());
    }
  }

  private Entry findEntryAfter(RecentChange c, String path) {
    return c.getRevisionAfter().getEntry().findEntry(path);
  }

  private Entry findEntryBefore(RecentChange c, String path) {
    return c.getRevisionBefore().getEntry().findEntry(path);
  }
}
