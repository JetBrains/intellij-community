// "Replace 'StringBuilder' with 'String'" "true-preview"

class RepeatWithAppend {
  String foo() {
    StringBuilder <caret>sb = new StringBuilder();
    sb.append("prefix");
    sb.repeat(" ", 100);
    sb.append("suffix");
    return sb.toString();
  }
}
