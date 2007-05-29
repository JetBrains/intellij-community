package com.intellij.localvcs.integration.ui.models;

import com.intellij.diff.Block;
import com.intellij.localvcs.core.LocalVcs;
import com.intellij.localvcs.core.LocalVcsTestCase;
import com.intellij.localvcs.core.TestLocalVcs;
import com.intellij.localvcs.core.revisions.Revision;
import org.junit.Test;

import java.util.List;

public class SelectedBlockCalculatorTest extends LocalVcsTestCase {
  LocalVcs vcs = new TestLocalVcs();

  @Test
  public void testSelectionWasNotChanged() {
    List<Revision> rr = createRevisions("abc\ndef\nghi", "abc\ndef\nghi");
    SelectedBlockCalculator c = new SelectedBlockCalculator(rr, 0, 2);

    Block b0 = c.getBlock(rr.get(0));
    Block b1 = c.getBlock(rr.get(1));

    assertBlock(0, 2, "abc\ndef\nghi", b0);
    assertBlock(0, 2, "abc\ndef\nghi", b1);
  }

  @Test
  public void testSelectionWasMoved() {
    List<Revision> rr = createRevisions("abc\ndef\nghi", "def\nghi");
    SelectedBlockCalculator c = new SelectedBlockCalculator(rr, 0, 1);

    Block b0 = c.getBlock(rr.get(0));
    Block b1 = c.getBlock(rr.get(1));

    assertBlock(0, 1, "def\nghi", b0);
    assertBlock(1, 2, "def\nghi", b1);
  }

  @Test
  public void testSelectionForVeryOldRevisionTakenBackward() {
    List<Revision> rr = createRevisions("ghi\nabc\ndef", "abc\nghi\ndef", "abc\ndef\nghi");
    SelectedBlockCalculator c = new SelectedBlockCalculator(rr, 0, 1);

    Block b2 = c.getBlock(rr.get(2));
    Block b1 = c.getBlock(rr.get(1));
    Block b0 = c.getBlock(rr.get(0));

    assertBlock(0, 1, "abc\ndef", b0);
    assertBlock(0, 2, "abc\nghi\ndef", b1);
    assertBlock(1, 2, "abc\ndef", b2);
  }

  private List<Revision> createRevisions(String... contents) {
    vcs.createFile("f", cf(contents[0]), -1);
    for (int i = 1; i < contents.length; i++) {
      vcs.changeFileContent("f", cf(contents[i]), -1);
    }
    return vcs.getRevisionsFor("f");
  }

  private void assertBlock(int from, int to, String content, Block b) {
    assertEquals(from, b.getStart());
    assertEquals(to, b.getEnd());
    assertEquals(content, b.getBlockContent());
  }
}
