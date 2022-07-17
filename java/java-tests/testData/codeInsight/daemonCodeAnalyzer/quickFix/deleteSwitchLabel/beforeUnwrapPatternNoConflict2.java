// "Remove unreachable branches" "true"
import java.util.concurrent.ThreadLocalRandom;
class Test {
  void test(Number n) {
      n = 1;
      switch (n) {
        case <caret>Integer i && i == 1:
          int rand = ThreadLocalRandom.current().nextInt();
          if (rand > 10) {
            i = 2;
          }
          else {
            i = 3;
          }
          System.out.println(i);
          break;
        case Long s:
          System.out.println(s);
          break;
        default:
          System.out.println();
          break;
      }
  }
}