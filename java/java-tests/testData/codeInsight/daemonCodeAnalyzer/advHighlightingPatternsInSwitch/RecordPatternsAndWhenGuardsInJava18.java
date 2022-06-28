class RecordPatternsAndWhenGuardsInJava18 {
  void test(Object o) {
    switch (o) {
      case <error descr="Pattern guards and record patterns are not supported at language level '18'">MyRecord(int x) r</error> -> {

      }
      case String s when <error descr="Cannot resolve symbol 's'">s</error>.length() > 10 -> {

      }
      default -> {

      }
    }
  }

}

record MyRecord(int x) {}
