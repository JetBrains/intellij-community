package com.intellij.localvcs.integration.ui.models;

import com.intellij.diff.Block;
import com.intellij.localvcs.core.ContentFactory;
import com.intellij.localvcs.core.InMemoryLocalVcs;
import com.intellij.localvcs.core.LocalVcs;
import com.intellij.localvcs.core.LocalVcsTestCase;
import com.intellij.localvcs.core.revisions.Revision;
import org.junit.Test;

import java.util.List;

public class SelectionCalculatorTest extends LocalVcsTestCase {
  LocalVcs vcs = new InMemoryLocalVcs();

  @Test
  public void testSelectionWasNotChanged() {
    List<Revision> rr = createRevisions("abc\ndef\nghi", "abc1\ndef1\nghi1");
    SelectionCalculator c = new SelectionCalculator(rr, 0, 2);

    Block b0 = c.getSelectionFor(rr.get(0));
    Block b1 = c.getSelectionFor(rr.get(1));

    assertBlock(0, 2, "abc1\ndef1\nghi1", b0);
    assertBlock(0, 2, "abc\ndef\nghi", b1);
  }

  @Test
  public void testSelectionWasMoved() {
    List<Revision> rr = createRevisions("abc\ndef\nghi", "def\nghi");
    SelectionCalculator c = new SelectionCalculator(rr, 0, 1);

    Block b0 = c.getSelectionFor(rr.get(0));
    Block b1 = c.getSelectionFor(rr.get(1));

    assertBlock(0, 1, "def\nghi", b0);
    assertBlock(1, 2, "def\nghi", b1);
  }

  @Test
  public void testSelectionForVeryOldRevisionTakenBackward() {
    List<Revision> rr = createRevisions("ghi\nabc\ndef", "abc\nghi\ndef", "abc\ndef\nghi");
    SelectionCalculator c = new SelectionCalculator(rr, 0, 1);

    Block b2 = c.getSelectionFor(rr.get(2));
    Block b1 = c.getSelectionFor(rr.get(1));
    Block b0 = c.getSelectionFor(rr.get(0));

    assertBlock(0, 1, "abc\ndef", b0);
    assertBlock(0, 2, "abc\nghi\ndef", b1);
    assertBlock(1, 2, "abc\ndef", b2);
  }

  @Test
  public void testNormailingLineEnds() {
    List<Revision> rr = createRevisions("abc\ndef\nghi", "abc\r\ndef\r\nghi");
    SelectionCalculator c = new SelectionCalculator(rr, 0, 1);

    Block b0 = c.getSelectionFor(rr.get(0));
    Block b1 = c.getSelectionFor(rr.get(1));

    assertBlock(0, 1, "abc\ndef", b0);
    assertBlock(0, 1, "abc\ndef", b1);
  }

  @Test
  public void testCanNotCalculateIfThereWasUnavailableContent() {
    List<Revision> rr = createRevisions(cf("one"), bigContentFactory(), cf("two"));

    SelectionCalculator c = new SelectionCalculator(rr, 0, 0);

    assertTrue(c.canCalculateFor(rr.get(0)));
    assertFalse(c.canCalculateFor(rr.get(1)));
    assertFalse(c.canCalculateFor(rr.get(2)));
  }

  private List<Revision> createRevisions(String... contents) {
    ContentFactory[] ff = new ContentFactory[contents.length];
    for (int i = 0; i < contents.length; i++) {
      ff[i] = cf(contents[i]);
    }
    return createRevisions(ff);
  }

  private List<Revision> createRevisions(ContentFactory... ff) {
    vcs.createFile("f", ff[0], -1);
    for (int i = 1; i < ff.length; i++) {
      vcs.changeFileContent("f", ff[i], -1);
    }
    return vcs.getRevisionsFor("f");
  }

  private void assertBlock(int from, int to, String content, Block b) {
    assertEquals(from, b.getStart());
    assertEquals(to, b.getEnd());
    assertEquals(content, b.getBlockContent());
  }
}
