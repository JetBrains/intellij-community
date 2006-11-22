package com.intellij.localvcs;

import org.junit.Test;

public class RootsTest extends TestCase {
  @Test
  public void testCreatingRoots() {
    Roots roots = new Roots();

    roots.createRoot("c:/root1");
    roots.createRoot("c:/root2");

    roots.hasEntry("c:/root1");
    roots.hasEntry("c:/root2");
  }

  @Test
  public void testCreatingEntriesUnderRoot() {
    Roots roots = new Roots();
    roots.createRoot("c:/root");

    roots.createFile(null, "c:/root/entry", null, null);

    assertTrue(roots.hasEntry("c:/root/entry"));
  }

  @Test
  public void testMovingEntriesBetweenRoots() {
    Roots roots = new Roots();
    roots.createRoot("c:/root1");
    roots.createRoot("c:/root2");

    roots.createFile(null, "c:/root1/file", null, null);
    roots.move("c:/root1/file", "c:/root2", null);

    assertFalse(roots.hasEntry("c:/root1/file"));
    assertTrue(roots.hasEntry("c:/root2/file"));
  }

//    @Test
//  public void testCantCreateEntryNotUnderExistingRoots() {
//    Roots roots = new Roots();
//    roots.createRoot("c:/root");
//
//    roots.createFile(null, "c:/another root/file", null, null);
//    roots.move("c:/root1/file", "c:/root2", null);
//
//    assertFalse(roots.hasEntry("c:/root1/file"));
//    assertTrue(roots.hasEntry("c:/root2/file"));
//  }
//
//  @Test
//public void testCantCreateEntryUnderRoots() {
//  Roots roots = new Roots();
//  roots.createRoot("c:/root");
//
//  roots.createFile(null, "c:/another root/file", null, null);
//  roots.move("c:/root1/file", "c:/root2", null);
//
//  assertFalse(roots.hasEntry("c:/root1/file"));
//  assertTrue(roots.hasEntry("c:/root2/file"));
//}
}
