class Main {
  void test(int x, int y) {
    System.out.println(switch(x) {
      case 1 -> false;
      default -> {
        <caret>  yield y <= 0;
      }
    });
  }
}