class RecordPatternsAndWhenGuardsInJava18 {
  void test(Object o) {
    switch (o) {
      case <error descr="Pattern guards and record patterns are not supported at language level '18'">MyRecord(int x) r</error> -> {

      }
      case <error descr="Pattern guards and record patterns are not supported at language level '18'">String s when s.length() > 10</error> -> {

      }
      default -> {

      }
    }
  }

}

record MyRecord(int x) {}
