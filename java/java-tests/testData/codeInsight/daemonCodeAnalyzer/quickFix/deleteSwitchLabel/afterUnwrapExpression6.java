// "Remove unreachable branches" "true-preview"
class Main {
  void foo(int x) {
    if (x == 42) {
      System.out.println(switch (x) {
          // 1
          // 2
          // 3
          // 4
          // 5
          // 6
          // 7
          case 42 -> {
          System.out.println("something");
          yield "Six by nine"; // 42
        }
        default -> {
          System.out.println("something");
          yield "many";
        }
      });
    }
  }
}

