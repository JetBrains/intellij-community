// "Replace 'StringBuilder' with 'String'" "false"

class RepeatCharArgument {
  String foo() {
    StringBuilder <caret>sb = new StringBuilder();
    sb.repeat('a', 100);
    return sb.toString();
  }
}
