// "Replace 'StringBuilder' with 'String'" "true-preview"

class RepeatWithMethodCallArg {
  String getStr() { return " "; }

  String foo() {
    StringBuilder <caret>sb = new StringBuilder();
    sb.repeat(getStr(), 5);
    return sb.toString();
  }
}
