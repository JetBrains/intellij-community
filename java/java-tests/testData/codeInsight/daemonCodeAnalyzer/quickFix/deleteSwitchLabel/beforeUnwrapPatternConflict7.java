// "Remove unreachable branches" "true"
class Test {

  int test(Number n) {
      n = 1;
      System.out.println(switch (n) {
        case Long l -> l.intValue();
        case <caret>Object i -> (int)i;
      } + 10);
      int i = 5;
      System.out.println(i);
  }
}