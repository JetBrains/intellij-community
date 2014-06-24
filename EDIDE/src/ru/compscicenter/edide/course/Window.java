package ru.compscicenter.edide.course;

/**
 * User: lia
 * Date: 21.06.14
 * Time: 18:54
 */
public class Window {
  private int line;
  private int start;
  private String text;
  private String hint;
  private String possibleAnswer;

  public int getLine() {
    return line;
  }

  public void setLine(int line) {
    this.line = line;
  }


  public String getText() {
    return text;
  }

  public void setText(String text) {
    this.text = text;
  }
}
