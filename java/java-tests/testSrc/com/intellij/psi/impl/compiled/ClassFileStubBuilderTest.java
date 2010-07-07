package com.intellij.psi.impl.compiled;

import com.intellij.util.containers.ContainerUtil;
import junit.framework.TestCase;

import java.util.*;

/**
 * Pavel.Fatin, 18.02.2010
 */
public class ClassFileStubBuilderTest extends TestCase {
  public void testOneFile() {
    assertTopLevel("");
    assertTopLevel("$");
    assertTopLevel("$$");
    assertTopLevel("$$$");
    assertTopLevel("Foo");
    assertTopLevel("Foo$");
    assertTopLevel("$Foo");
    assertTopLevel("$Foo$");
    assertTopLevel("Foo$Bar");
    assertTopLevel("$Foo$Bar");
    assertTopLevel("Foo$Bar$");
    assertTopLevel("$Foo$Bar$");
    assertTopLevel("$$Foo$$Bar$$");
  }

  public void testWrongCase() {
    assertTopLevel("Foo$Bar", "foo");
    assertTopLevel("foo$Bar", "Foo");
  }

  public void testShortName() {
    assertTopLevel("A");

    assertInner("Foo$B", "Foo");
    assertInner("A$Foo", "A");
    assertInner("A$B", "A");

    assertTopLevel("A$B", "A$");
    assertTopLevel("A$B", "$B");
    assertTopLevel("A$B", "B");
    assertTopLevel("A$B", "$");
  }

  public void testShortNames() {
    assertInner("A$B$C", "A");
    assertInner("A$B$C", "A$B");
    assertInner("A$B$C", "A", "A$B");

    assertTopLevel("A$B$C", "A$");
    assertTopLevel("A$B$C", "A$B$");
  }

  public void testEmptyFileName() {
    assertTopLevel("$Foo", "");
  }

  public void testNoInnerName() {
    //assertTopLevel("Foo$", "Foo");
    //assertTopLevel("Foo$$", "Foo$");
    // an exception to the rule (to hide Scala "objects") 
    assertInner("Foo$", "Foo");
    assertInner("Foo$$", "Foo$");
  }

  public void testDollarsInName() {
    assertInner("$Foo$Bar", "$Foo");
    assertInner("Foo$$Bar", "Foo");
    assertInner("Foo$$Bar", "Foo");
    assertInner("$Foo$$Bar", "$Foo");
    assertInner("$Foo$$Bar", "$Foo$");
  }

  public void testInner() {
    assertInner("Foo$Bar", "Foo");
    assertInner("Foo$bar", "Foo");
    assertInner("foo$Bar", "foo");
    assertInner("foo$bar", "foo");
  }
  
  public void testInnerAndNoise() {
    assertInner("Foo$Bar", "Foo", "Foo$", "$Bar", "Bar");
  }

  public void testTopLevel() {
    assertTopLevel("Foo$Bar");
    assertTopLevel("Foo$Bar", "Foo$");
    assertTopLevel("Foo$Bar", "$Bar");
    assertTopLevel("Foo$Bar", "Bar");
    assertTopLevel("Foo$Bar", "Foo$", "$Bar", "Bar");
  }

  public void testInners() {
    assertInner("Foo$Bar$Moo", "Foo");
    assertInner("Foo$Bar$Moo", "Foo$Bar");
    assertInner("Foo$Bar$Moo", "Foo", "Foo$Bar");
  }

  public void testInnersAndNoise() {
    assertInner("Foo$Bar$Moo", "Foo", "$Bar", "Bar", "$Moo", "Moo", "Bar$Moo", "$Bar$Moo");
    assertInner("Foo$Bar$Moo", "Foo$Bar", "$Bar", "Bar", "$Moo", "Moo", "Bar$Moo", "$Bar$Moo");
    assertInner("Foo$Bar$Moo", "Foo", "Foo$Bar", "$Bar", "Bar", "$Moo", "Moo", "Bar$Moo", "$Bar$Moo");
  }

  public void testTopLevels() {
    assertTopLevel("Foo$Bar$Moo");
    assertTopLevel("Foo$Bar$Moo", "$Bar");
    assertTopLevel("Foo$Bar$Moo", "Bar");
    assertTopLevel("Foo$Bar$Moo", "$Moo");
    assertTopLevel("Foo$Bar$Moo", "Moo");
    assertTopLevel("Foo$Bar$Moo", "Bar$Moo");
    assertTopLevel("Foo$Bar$Moo", "$Bar$Moo");
    assertTopLevel("Foo$Bar$Moo", "$Bar", "Bar", "$Moo", "Moo", "Bar$Moo", "$Bar$Moo");
  }

  private static void assertInner(String name, String... files) {
    assertTrue("Class " + name + " must be identified as inner", isInner(name, files));
  }

  private static void assertTopLevel(String name, String... files) {
    assertFalse("Class " + name + " must be identified as top-level", isInner(name, files));
  }

  private static boolean isInner(String name, String... files) {
    Set<String> all = new HashSet<String>();
    ContainerUtil.addAll(all, files);
    all.add(name);
    return ClassFileStubBuilder.isInner(name, new DirectoryMock(all));
  }


  private static class DirectoryMock implements ClassFileStubBuilder.Directory {
    private Set<String> myFiles;

    private DirectoryMock(Set<String> files) {
      myFiles = files;
    }

    public boolean contains(String name) {
      return myFiles.contains(name);
    }
  }
}
