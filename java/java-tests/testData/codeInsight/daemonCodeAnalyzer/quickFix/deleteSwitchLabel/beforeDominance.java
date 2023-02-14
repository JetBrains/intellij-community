// "Remove switch branch 'Integer i'" "true-preview"
class Test {
  int foo(Object o) {
    return switch (o) {
      case Number n -> 1;
      case Integer i<caret> -> 2;
      default -> 3;
    };
  }
}