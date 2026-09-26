// "Replace 'StringBuilder' with 'String'" "true-preview"

class RepeatWithAppend {
  String foo() {
      String sb = "prefix" +
              " ".repeat(100) +
              "suffix";
    return sb;
  }
}
