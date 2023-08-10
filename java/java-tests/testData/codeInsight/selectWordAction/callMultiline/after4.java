class X {
  void test(List<String> list) {
<selection>    list.stream()
      .m<caret>ap(x -> x)
</selection>      .collect(toList());
  }
}
