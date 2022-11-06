class X {
  void test(List<String> list) {
    list.stream()
      .<selection>m<caret>ap</selection>(x -> x)
      .collect(toList());
  }
}
