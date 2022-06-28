class RecordPatternsAndWhenGuardsInJava18 {
  void test(Object o) {
    switch (o) {
      case MyRecord(int x) r -> {

      }
      case String s when <error descr="Cannot resolve symbol 's'">s</error>.length() > 10 -> {

      }
      default -> {

      }
    }
  }

}

record MyRecord(int x) {}
