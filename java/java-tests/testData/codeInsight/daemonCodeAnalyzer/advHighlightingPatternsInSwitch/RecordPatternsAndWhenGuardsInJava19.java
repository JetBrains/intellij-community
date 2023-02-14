class RecordPatternsAndWhenGuardsInJava18 {
  void test(Object o) {
    switch (o) {
      case MyRecord(int x) r -> {

      }
      case String s when s.length() > 10 -> {

      }
      default -> {

      }
    }
  }

}

record MyRecord(int x) {}
