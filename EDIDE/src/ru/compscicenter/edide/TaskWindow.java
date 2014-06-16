package ru.compscicenter.edide;

/**
 * User: lia
 * Date: 30.05.14
 * Time: 20:34
 */
public class TaskWindow {
  private int line;
  private int startOffset;
  private String text;
  private String docsFile;
  private String possibleAnswer;

  public TaskWindow(int line, int startOffset, String text,
                    String docsFile, String possibleAnswer) {
    this.line = line;
    this.startOffset = startOffset;
    this.text = text;
    this.docsFile = docsFile;
    this.possibleAnswer = possibleAnswer;
  }

  public int getLine() {
    return line;
  }

  public String getPossibleAnswer() {
    return possibleAnswer;
  }

  public int getStartOffset() {
    return startOffset;
  }

  public String getText() {
    return text;
  }

  public String getDocsFile() {
    return docsFile;
  }
}
