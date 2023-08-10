public class SemicolonAfterSwitchExpression {
  void test() {
      int x = switch (0) {
          case 0 -> 1;
          case 1 -> 2;
          default -> 3;
      };
  }
}