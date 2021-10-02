// "Remove switch branch 'Integer i'" "true"
class Test {
  int foo(Object o) {
    return switch (o) {
      case Number n -> 1;
        default -> 3;
    };
  }
}