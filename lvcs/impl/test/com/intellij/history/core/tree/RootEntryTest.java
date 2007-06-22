package com.intellij.history.core.tree;

import com.intellij.history.core.LocalVcsTestCase;
import org.junit.Before;
import org.junit.Test;

public class RootEntryTest extends LocalVcsTestCase {
  private Entry root;
  private Entry child;

  @Before
  public void setUp() {
    root = new RootEntry();
    child = new DirectoryEntry(1, "child");
    root.addChild(child);
  }

  @Test
  public void testIdPathToChildren() {
    assertEquals(idp(-1, 1), child.getIdPath());
  }

  @Test
  public void testPathToChildren() {
    assertEquals("child", child.getPath());
  }

  @Test
  public void testCopying() {
    Entry copy = root.copy();

    assertEquals(RootEntry.class, copy.getClass());
    assertEquals(1, copy.getChildren().size());

    assertNotSame(child, copy.getChildren().get(0));
    assertEquals("child", copy.getChildren().get(0).getName());
  }
}
