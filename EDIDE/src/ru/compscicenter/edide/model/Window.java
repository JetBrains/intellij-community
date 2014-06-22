package ru.compscicenter.edide.model;

/**
 * User: lia
 * Date: 21.06.14
 * Time: 18:54
 */
public class Window {
  private int line;
  private int offset;
  private String text;

  public int getLine() {
    return line;
  }

  public void setLine(int line) {
    this.line = line;
  }

  public int getOffset() {
    return offset;
  }

  public void setOffset(int offset) {
    this.offset = offset;
  }

  public String getText() {
    return text;
  }

  public void setText(String text) {
    this.text = text;
  }
}
