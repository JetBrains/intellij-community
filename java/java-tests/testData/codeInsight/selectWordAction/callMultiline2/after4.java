class X {
  void test(List<String> list) {
    <selection>list.stream()
      .map(x <caret>-> x)</selection>
      .collect(toList());
  }
}
