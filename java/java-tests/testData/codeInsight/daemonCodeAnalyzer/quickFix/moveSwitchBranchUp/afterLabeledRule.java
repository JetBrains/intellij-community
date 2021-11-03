// "Move switch branch 'Integer i' before 'Number n'" "true"
class Main {
  void test(Object o) {
    int x = switch (o) {
        case Integer i -> 1;
        case Number n -> 2;
      case String s -> 3;
        default -> 4;
    };
  }
}