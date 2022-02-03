public class AlwaysFalseForLoop {
  void test() {
    int i = 0;
    for(i++; <error descr="Loop condition is always false making the loop body unreachable">fa<caret>lse</error>; System.out.println("oops")) {
      System.out.println("oops");
    }

  }

  void test2() {
    for(int j=0; <error descr="Loop condition is always false making the loop body unreachable">false</error>; j++) {

    }
  }
}