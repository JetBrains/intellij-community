package com.intellij.openapi.editor;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * @author Denis Zhdanov
 * @since 06/29/2010
 */
public class TextChangeTest {

  @Test(expected = IllegalArgumentException.class)
  public void negativeStartIndex() {
    new TextChange("", -1);
  }

  @Test(expected = IllegalArgumentException.class)
  public void negativeEndIndex() {
    new TextChange("", 0, -1);
  }

  @Test(expected = IllegalArgumentException.class)
  public void inconsistentIndices() {
    new TextChange("", 2, 1);
  }

  @Test
  public void propertiesExposing() {
    int start = 1;
    int end = 10;
    String text = "test";
    TextChange textChange = new TextChange(text, start, end);
    assertEquals(text, textChange.getText());
    assertArrayEquals(text.toCharArray(), textChange.getChars());
    assertEquals(start, textChange.getStart());
    assertEquals(end, textChange.getEnd());
  }

  @Test(expected = IllegalArgumentException.class)
  public void advanceToNegativeOffset() {
    new TextChange("", 1, 2).advance(-2);
  }

  @Test
  public void advance() {
    TextChange base = new TextChange("xyz", 3, 5);

    int[] offsets = {5, 0, -3};
    for (int offset : offsets) {
      assertEquals(new TextChange(base.getText(), base.getStart() + offset, base.getEnd() + offset), base.advance(offset));
    }
  }
}
