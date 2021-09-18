class Test {
  public static void main(String... args) {
    final Boolean or = java.util.stream.Stream.of(foo(), foo()).reduce(false, Boolean::logicalOr);
    final Boolean or2 = <warning descr="Result of 'java.util.stream.Stream.of(foo(), foo()).reduce(true, Boolean::logicalOr)' is always 'true'">java.util.stream.Stream.of(foo(), foo()).reduce(true, <warning descr="Method reference result is always 'true'">Boolean::logicalOr</warning>)</warning>;
    final Boolean and = java.util.stream.Stream.of(foo(), foo()).reduce(true, Boolean::logicalAnd);
    final Boolean and2 = <warning descr="Result of 'java.util.stream.Stream.of(foo(), foo()).reduce(false, Boolean::logicalAnd)' is always 'false'">java.util.stream.Stream.of(foo(), foo()).reduce(false, <warning descr="Method reference result is always 'false'">Boolean::logicalAnd</warning>)</warning>;
    java.util.stream.Stream.of(true, true).reduce(Boolean::logicalAnd).ifPresent(System.out::println);
    java.util.stream.Stream.of(true, false).reduce(<warning descr="Method reference result is always 'false'">Boolean::logicalAnd</warning>).ifPresent(System.out::println);
    java.util.stream.Stream.of(false, true).reduce(Boolean::logicalAnd).ifPresent(System.out::println);
    java.util.stream.Stream.of(false, false).reduce(<warning descr="Method reference result is always 'false'">Boolean::logicalAnd</warning>).ifPresent(System.out::println);
  }

  native static boolean foo();
}