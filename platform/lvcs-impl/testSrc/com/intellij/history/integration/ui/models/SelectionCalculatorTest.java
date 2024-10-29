// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.history.integration.ui.models;

import com.intellij.diff.Block;
import com.intellij.history.core.InMemoryLocalHistoryFacade;
import com.intellij.history.core.LocalHistoryFacade;
import com.intellij.history.core.LocalHistoryTestCase;
import com.intellij.history.core.tree.RootEntry;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.platform.lvcs.impl.RevisionId;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.easymock.EasyMock.*;

public class SelectionCalculatorTest extends LocalHistoryTestCase {
  public static final String PATH = "f";
  IdeaGateway gw = new MyIdeaGateway();
  LocalHistoryFacade vcs = new InMemoryLocalHistoryFacade();

  @Test
  public void testSelectionWasNotChanged() {
    RootEntry rootEntry = new RootEntry();
    List<RevisionId> rr = createRevisions(rootEntry, "abc\ndef\nghi", "abc1\ndef1\nghi1");
    SelectionCalculator c = SelectionCalculator.create(vcs, gw, rootEntry, PATH, rr,  0, 2);

    Block b0 = c.getSelectionFor(rr.get(0), Progress.EMPTY);
    Block b1 = c.getSelectionFor(rr.get(1), Progress.EMPTY);

    assertBlock(0, 3, "abc1\ndef1\nghi1", b0);
    assertBlock(0, 3, "abc\ndef\nghi", b1);
  }

  @Test
  public void testSelectionWasMoved() {
    RootEntry rootEntry = new RootEntry();
    List<RevisionId> rr = createRevisions(rootEntry, "abc\ndef\nghi", "def\nghi");
    SelectionCalculator c = SelectionCalculator.create(vcs, gw, rootEntry, PATH, rr,  0, 1);

    Block b0 = c.getSelectionFor(rr.get(0), Progress.EMPTY);
    Block b1 = c.getSelectionFor(rr.get(1), Progress.EMPTY);

    assertBlock(0, 2, "def\nghi", b0);
    assertBlock(1, 3, "def\nghi", b1);
  }

  @Test
  public void testSelectionForVeryOldRevisionTakenBackward() {
    RootEntry rootEntry = new RootEntry();
    List<RevisionId> rr = createRevisions(rootEntry, "ghi\nabc\ndef", "abc\nghi\ndef", "abc\ndef\nghi");
    SelectionCalculator c = SelectionCalculator.create(vcs, gw, rootEntry, PATH, rr, 0, 1);

    Block b2 = c.getSelectionFor(rr.get(2), Progress.EMPTY);
    Block b1 = c.getSelectionFor(rr.get(1), Progress.EMPTY);
    Block b0 = c.getSelectionFor(rr.get(0), Progress.EMPTY);

    assertBlock(0, 2, "abc\ndef", b0);
    assertBlock(0, 3, "abc\nghi\ndef", b1);
    assertBlock(1, 3, "abc\ndef", b2);
  }

  @Test
  public void testNormalizingLineEnds() {
    RootEntry rootEntry = new RootEntry();
    List<RevisionId> rr = createRevisions(rootEntry, "abc\ndef\nghi", "abc\r\ndef\r\nghi");
    SelectionCalculator c = SelectionCalculator.create(vcs, gw, rootEntry, PATH, rr, 0, 1);

    Block b0 = c.getSelectionFor(rr.get(0), Progress.EMPTY);
    Block b1 = c.getSelectionFor(rr.get(1), Progress.EMPTY);

    assertBlock(0, 2, "abc\ndef", b0);
    assertBlock(0, 2, "abc\ndef", b1);
  }

  @Test
  public void testProgressOnGetSelection() {
    RootEntry rootEntry = new RootEntry();
    List<RevisionId> rr = createRevisions(rootEntry, "one", "two", "three", "four");
    SelectionCalculator c = SelectionCalculator.create(vcs, gw, rootEntry, PATH, rr, 0, 0);

    Progress p = createStrictMock(Progress.class);
    p.processed(25);
    p.processed(50);
    p.processed(75);
    p.processed(100);
    replay(p);

    c.getSelectionFor(rr.get(3), p);

    verify(p);
  }

  @Test
  public void testProgressOnCanCalculate() {
    RootEntry rootEntry = new RootEntry();
    List<RevisionId> rr = createRevisions(rootEntry, "one", "two");
    SelectionCalculator c = SelectionCalculator.create(vcs, gw, rootEntry, PATH, rr, 0, 0);

    Progress p = createMock(Progress.class);
    p.processed(50);
    p.processed(100);
    replay(p);

    c.canCalculateFor(rr.get(1), p);

    verify(p);
  }

  private @NotNull List<RevisionId> createRevisions(@NotNull RootEntry rootEntry, String... contents) {
    vcs.addChangeInTests(createFile(rootEntry, PATH, contents[0], -1, false));
    for (int i = 1; i < contents.length; i++) {
      vcs.addChangeInTests(changeContent(rootEntry, PATH, contents[i], i));
    }
    return collectRevisionIds(vcs, PATH, null);
  }

  private void assertBlock(int from, int to, String content, Block b) {
    assertEquals(from, b.getStart());
    assertEquals(to, b.getEnd());
    assertEquals(content, b.getBlockContent());
  }

  private static class MyIdeaGateway extends IdeaGateway {
    @Override
    public String stringFromBytes(byte @NotNull [] bytes, @NotNull String path) {
      return new String(bytes, StandardCharsets.UTF_8);
    }
  }
}
