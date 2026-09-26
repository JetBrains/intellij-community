// "Replace 'StringBuilder' with 'String'" "true-preview"

class Repeat {
  String foo() {
    StringBuilder <caret>sb = new StringBuilder();
    sb.repeat(" ", 100);
    return sb.toString();
  }
}
