public class Test {
  void testDominatedPatterns(Object o) {
    switch (o) {
      case String ss when true:
        break;
      case <error descr="Label is dominated by a preceding case label 'String ss'">String ss</error>:
        break;
      default:
        break;
    }
  }

  int testDominatedConstLabel(Integer i, E e) {
    switch (e) {
      case <warning descr="Switch label 'E d' is the only reachable in the whole switch">E d</warning> when true: return 1;
      case <error descr="Label is dominated by a preceding case label 'E d'">A</error>: return -1;
    }

    return switch (i) {
      case Integer ii when true -> 1;
      case <error descr="Label is dominated by a preceding case label 'Integer ii'">2</error> -> 2;
      default -> 3;
    };
  }

  enum E {A, B}
}
