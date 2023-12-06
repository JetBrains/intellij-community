import java.util.Optional;

class Main {
  interface IT {
  }

  sealed interface IT2 {
  }

  enum T implements IT, IT2 {
    A, B, C;

    public void test2(IT2 t2) {
      switch (t2) {
        case T.A -> System.out.println("1");
        case B -> System.out.println("2");
        case T.C -> System.out.println("3");
      }
    }
  }

  public static void main(String[] args) {

  }

  public void test0(IT2 t2) {
    switch (t2) {
      case T.A -> System.out.println(1);
      case T.B -> System.out.println(2);
      case T.C -> System.out.println(3);
    }
  }

  public void test1(IT t2) {
    switch (<error descr="'switch' statement does not cover all possible input values">t2</error>) {
      case T.A -> System.out.println(1);
      case T.B -> System.out.println(2);
      case T.C -> System.out.println(3);
    }
  }

  public void test2(IT t2) {
    switch (t2) {
      case T.A -> System.out.println("1");
      case <error descr="Cannot resolve symbol 'B'">B</error> -> System.out.println("2");
      case T.C -> System.out.println("3");
    }
  }
}