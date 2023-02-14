public class Test {
  void testDominatedPatterns(Object o) {
    switch (o) {
      case String ss && true:
        break;
      case <error descr="Label is dominated by a preceding case label 'String ss && true'">String ss</error>:
        break;
      case default:
        break;
    }
  }

  int testDominatedConstLabel(Integer i, E e) {
    switch (e) {
      case (E d && d == E.A): return 1;
      case <error descr="Label is dominated by a preceding case label '(E d && d == E.A)'">A</error>: return -1;
    }

    return switch (i) {
      case (Integer ii && ii > 2) -> 1;
      case <error descr="Label is dominated by a preceding case label '(Integer ii && ii > 2)'">2</error> -> 2;
      case default -> 3;
    };
  }

  enum E {A, B}
}
