// "Replace 'StringBuilder' with 'String'" "true-preview"

class RepeatWithMethodCallArg {
  String getStr() { return " "; }

  String foo() {
      return String.valueOf(getStr()).repeat(5);
  }
}
