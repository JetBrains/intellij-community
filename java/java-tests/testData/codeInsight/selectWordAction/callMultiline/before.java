class X {
  void test(List<String> list) {
    list.stream()
      .m<caret>ap(x -> x)
      .collect(toList());
  }
}
