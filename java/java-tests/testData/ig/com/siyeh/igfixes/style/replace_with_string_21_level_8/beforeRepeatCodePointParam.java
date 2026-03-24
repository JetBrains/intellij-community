// "Replace 'StringBuilder' with 'String'" "false"

class RepeatCodePointArgument {
  String foo() {
    StringBuilder <caret>sb = new StringBuilder();
    sb.repeat(7, 100);
    return sb.toString();
  }
}
