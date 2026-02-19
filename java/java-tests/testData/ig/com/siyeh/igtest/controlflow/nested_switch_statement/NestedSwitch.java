class MyTest {
  int y(int i) {
    return switch (i) {
      case 0 -> 0;
      default -> <warning descr="Nested 'switch' expression">switch</warning> (i) {
        case 100 -> 0;
        default -> i;
      };
    };
  }
}