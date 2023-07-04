public class QualifiedEnumInSwitch {
  sealed interface IT{}
  enum T implements IT {A, B, C,}


  void f(T e1) {
    switch (e1) {
      case <error descr="Duplicate label 'A'">T.A</error>, T.B:
        break;
      case <error descr="Duplicate label 'A'">A</error>:
      default: break;
    }
  }

  public int test2(T t2) {
    switch (t2) {
      case T.A -> System.out.println(1);
      case B -> System.out.println(2);
      case T.C -> System.out.println(3);
    };

    return switch (t2) {
      case T.A -> 1;
      case B -> 1;
      case T.C -> 1;
    };
  }


  public static void main(String[] args) {

  }
}