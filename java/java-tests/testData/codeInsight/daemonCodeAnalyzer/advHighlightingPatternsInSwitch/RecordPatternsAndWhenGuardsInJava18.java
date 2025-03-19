class RecordPatternsAndWhenGuardsInJava18 {
  void test(Object o) {
    switch (o) {
      case MyRecord(int <error descr="Patterns in switch are not supported at language level '18'">x</error>) <error descr="Identifier is not allowed here"><error descr="Patterns in switch are not supported at language level '18'">r</error></error> -> {

      }
      case String <error descr="Patterns in switch are not supported at language level '18'">s</error> when s.length() > 10 -> {

      }
      default -> {

      }
    }
  }

}

record MyRecord(int x) {}
