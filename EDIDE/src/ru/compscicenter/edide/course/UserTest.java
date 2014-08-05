package ru.compscicenter.edide.course;

/**
 * author: liana
 * data: 8/5/14.
 */
public class UserTest {
  private String input;
  private String output;
  private StringBuffer myInputBuffer = new StringBuffer();
  private StringBuffer myOutputBuffer =  new StringBuffer();

  public String getInput() {
    return input;
  }

  public void setInput(String input) {
    this.input = input;
  }

  public String getOutput() {
    return output;
  }

  public void setOutput(String output) {
    this.output = output;
  }

  public StringBuffer getInputBuffer() {
    return myInputBuffer;
  }

  public StringBuffer getOutputBuffer() {
    return myOutputBuffer;
  }
}
