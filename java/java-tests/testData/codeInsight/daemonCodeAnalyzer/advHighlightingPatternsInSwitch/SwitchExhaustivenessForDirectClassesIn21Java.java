import java.util.Optional;

class Main {
  class Case1{
    interface I{}

    sealed interface T permits A, B{}
    sealed interface A extends T permits EN{}

    enum EN implements A, I {EN_A, EN_B,}

    final class B implements T {

    }

    public static void t(T t) {
      switch (t) { //not error
        case EN.EN_A -> System.out.println(3);
        case EN.EN_B -> System.out.println(3);
        case I i -> System.out.println(1);
        case B b -> System.out.println(2);
      }
    }

    public static void t2(T t) {
      switch (<error descr="'switch' statement does not cover all possible input values">t</error>) { //error
        case EN.EN_B -> System.out.println(3);
        case I i -> System.out.println(1);
        case B b -> System.out.println(2);
      }
    }
  }

  class Case2{
    interface I{}

    sealed interface T permits A, B{}
    sealed interface A extends T permits R{}

    record R(CharSequence s) implements A, I{}

    final class B implements T {

    }

    public static void t(T t) {
      switch (t) { //not error
        case R(CharSequence s)-> System.out.println(3);
        case I i -> System.out.println(1);
        case B b -> System.out.println(2);
      }
    }

    public static void t2(T t) {
      switch (<error descr="'switch' statement does not cover all possible input values">t</error>) { //error
        case R(String s)-> System.out.println(3);
        case I i -> System.out.println(1);
        case B b -> System.out.println(2);
      }
    }
  }
}
