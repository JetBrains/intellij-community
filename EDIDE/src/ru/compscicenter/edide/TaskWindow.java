package ru.compscicenter.edide;

/**
 * User: lia
 * Date: 30.05.14
 * Time: 20:34
 * Frame with task implementation
 */
public class TaskWindow {
  private final int myLine;
  private final int myStartOffset;
  private final String myText;
  private final String myDocsFile;

    public TaskWindow(int line, int startOffset, String text,
                    String docsFile) {
    myLine = line;
    myStartOffset = startOffset;
    myText = text;
    myDocsFile = docsFile;
  }

  public int getLine() {
    return myLine;
  }

  public int getStartOffset() {
    return myStartOffset;
  }

  public String getText() {
    return myText;
  }

  public String getDocsFile() {
    return myDocsFile;
  }
}
