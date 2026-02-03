class X {
  void test(List<String> list) {
    list.stream()
      .<selection>m<caret>ap(x -> x)</selection>
      .collect(toList());
  }
}
