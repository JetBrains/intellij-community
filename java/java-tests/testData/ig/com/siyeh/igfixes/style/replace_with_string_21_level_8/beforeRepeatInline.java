// "Replace 'StringBuilder' with 'String'" "true-preview"

class RepeatInline {
  String foo() {
    return new <caret>StringBuilder().repeat(" ", 5).toString();
  }
}
