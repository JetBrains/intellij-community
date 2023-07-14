// "Remove unreachable branches" "true-preview"
class Main {
  void foo(int x) {
    if (x == 42) {
      System.out.println(switch (x) {
        case 1 -> "one"; // 1
        case 2 -> "two"; // 2
        case 3 -> "three"; // 3
        case 4 -> "four"; // 4
        case 5 -> "five"; // 5
        case 6 -> "six"; // 6
        case 7 -> "seven"; // 7
        case 42<caret> -> {
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

