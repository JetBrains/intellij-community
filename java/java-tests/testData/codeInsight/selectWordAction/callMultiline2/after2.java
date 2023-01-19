class X {
  void test(List<String> list) {
    list.stream()
      .map(<selection>x <caret>-> x</selection>)
      .collect(toList());
  }
}
