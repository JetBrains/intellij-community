class X {
  void test(List<String> list) {
    list.stream()
      .map(x <caret>-> x)
      .collect(toList());
  }
}
