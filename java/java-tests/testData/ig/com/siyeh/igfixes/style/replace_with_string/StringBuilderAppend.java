class StringBuilderAppend {
  String foo(int i) {
    return new Strin<caret>gBuilder().append("test: ").append(i).toString();
  }
}