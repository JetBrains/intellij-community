// "Remove unreachable branches" "true-preview"
class Main {
  void foo(int x) {
    if (x == 42) {
      System.out.println(switch (x) {
        case 42 -> {
          System.out.println("something");
          yield "Six by nine";
        }
          default -> "and more";
      });
    }
  }
}
