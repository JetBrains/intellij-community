public class Switches {

  enum E {
    A, B
  }

  int testReachability(E e) {
    switch (e) {
      case E d when d == E.A:
        return 1;
      case A:
        return -1;
      case B:
        return -2;
    }
    <error descr="Unreachable statement">return</error> 1;
  }

  sealed interface T {
  }

  final class T1 implements T {
  }

  final class T2 implements T {
  }

  void testReachability2(T i) {
    switch (i) {
      case T1 f:
        throw new RuntimeException();
      case T2 s:
        throw new RuntimeException();
    }
    <error descr="Unreachable statement">System.out.println();</error>
  }

  class Reachability2{
    sealed interface T{}
    final class T1 implements T{}

    int test(T t) {
      switch (t) {
        case T1  t1: return 2;
      }
    }
  }
}