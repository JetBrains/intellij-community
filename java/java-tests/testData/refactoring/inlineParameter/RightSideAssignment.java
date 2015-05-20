class Test {
  String foo(String content) {
    content = content.replace("a", "b");
    return con<caret>tent;
  }
}