// "Replace 'StringBuilder' with 'String'" "true"

class Repeat {
  String foo() {
    StringBuilder <caret>sb = new StringBuilder();
    sb.repeat((String) null, 100);
    return sb.toString();
  }
}
