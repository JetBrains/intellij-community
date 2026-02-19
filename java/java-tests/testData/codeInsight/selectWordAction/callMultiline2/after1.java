class X {
  void test(List<String> list) {
    list.stream()
      .map(x <selection><caret>-></selection> x)
      .collect(toList());
  }
}
