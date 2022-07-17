// "Move switch branch 'Integer i' before 'Number n'" "true"
class Main {
  void test(Object o) {
    int x = switch (o) {
      case Number n -> 2;
      case String s -> 3;
      case Integer i<caret> -> 1;
      default -> 4;
    };
  }
}