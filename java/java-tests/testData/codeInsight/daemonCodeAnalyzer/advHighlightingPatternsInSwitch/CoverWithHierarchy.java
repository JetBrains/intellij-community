class Main {
  sealed interface T permits T1, T2 {
  }
  final class T1 implements T {
  }

  interface B{}

  final class T2 implements T, B {
  }

  public static void test2(T t) {
    switch (t) {
      case T1 t1-> System.out.println(1);
      case B t2-> System.out.println(1);
    }
  }
}