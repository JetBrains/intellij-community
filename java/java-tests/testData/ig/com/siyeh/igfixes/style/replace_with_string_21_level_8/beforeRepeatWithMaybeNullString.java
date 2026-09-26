// "Replace 'StringBuilder' with 'String'" "true"

class Repeat {
  String foo(String string) {
    StringBuilder <caret>sb = new StringBuilder();
    sb.repeat(string, 100);
    return sb.toString();
  }
}
