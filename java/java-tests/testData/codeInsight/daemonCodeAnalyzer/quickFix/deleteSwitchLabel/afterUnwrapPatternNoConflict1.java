// "Remove unreachable branches" "true"
import java.util.concurrent.ThreadLocalRandom;

class Test {

  void test(Number n) {
      n = 1;
      Integer i = (Integer) n;
      int rand = ThreadLocalRandom.current().nextInt();
      if (rand > 10) {
        i = 2;
      }
      else {
        i = 3;
      }
      System.out.println(i);
  }
}