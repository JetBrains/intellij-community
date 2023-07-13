import java.util.Optional;




class CaseConcreateSealed{
  sealed class T{}
  final class T1 extends T{}
  final class T2 extends T{}

  record One(T t) {
  }

  public static void t(T t) {
    switch (<error descr="'switch' statement does not cover all possible input values">t</error>) {
      case T1 t1-> System.out.println("1");
      case T2 t2-> System.out.println("1");
    }
  }
  public static void t2(One one) {
    switch (<error descr="'switch' statement does not cover all possible input values">one</error>) {
      case One(T1 t)-> System.out.println("1");
      case One(T2 t)-> System.out.println("1");
    }
  }
}