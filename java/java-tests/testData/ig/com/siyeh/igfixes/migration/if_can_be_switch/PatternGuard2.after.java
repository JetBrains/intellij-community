import org.jetbrains.annotations.NotNull;

class Test {
  void test(@NotNull Object o, int variable) {
      <caret>switch (o) {
          case String s when !s.isEmpty() -> System.out.println();
          case Integer x when x > 0 && x < 10 -> System.out.println();
          case Integer x when x == 42 -> System.out.println();
          case Integer i -> System.out.println();
          case Float v when variable > 0 -> System.out.println();
          default -> {
          }
      }
  }
}