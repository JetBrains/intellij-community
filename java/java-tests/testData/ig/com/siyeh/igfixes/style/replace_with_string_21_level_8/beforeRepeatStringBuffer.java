// "Replace 'StringBuffer' with 'String'" "true-preview"

class RepeatStringBuffer {
  String foo() {
    StringBuffer <caret>sb = new StringBuffer();
    sb.repeat(" ", 100);
    return sb.toString();
  }
}
