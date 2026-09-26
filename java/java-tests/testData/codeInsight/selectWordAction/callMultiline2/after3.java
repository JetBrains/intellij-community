class X {
  void test(List<String> list) {
    list.stream()
      .<selection>map(x <caret>-> x)</selection>
      .collect(toList());
  }
}
