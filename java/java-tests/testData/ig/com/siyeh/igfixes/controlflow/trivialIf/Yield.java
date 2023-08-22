class Main {
  void test(int x, int y) {
    System.out.println(switch(x) {
      case 1 -> false;
      default -> {
        <caret>if (y > 0) yield false;
        yield true;
      }
    });
  }
}