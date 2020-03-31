class Test {
  public static void main(String... args) {
    java.util.stream.Stream.of(true, true).reduce(Boolean::logicalAnd).ifPresent(System.out::println);
    java.util.stream.Stream.of(true, false).reduce(<warning descr="Method reference result is always 'false'">Boolean::logicalAnd</warning>).ifPresent(System.out::println);
    java.util.stream.Stream.of(false, true).reduce(Boolean::logicalAnd).ifPresent(System.out::println);
    java.util.stream.Stream.of(false, false).reduce(<warning descr="Method reference result is always 'false'">Boolean::logicalAnd</warning>).ifPresent(System.out::println);
  }
}