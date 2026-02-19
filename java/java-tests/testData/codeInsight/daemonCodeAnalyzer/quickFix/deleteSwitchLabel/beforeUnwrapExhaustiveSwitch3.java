// "Remove unreachable branches" "false"

public class Switches {
  sealed interface I2{}
  record A(I2 x) implements I2{}
  record B() implements I2{}

  void foo(I2 x) {
    if (x instanceof B) {
      System.out.println(switch (x) {
        case A(A a)  -> {
          System.out.println("something");
          yield "1";
        }
        case A(B a)  -> {
          System.out.println("something");
          yield "1";
        }
        case B <caret>b  -> {
          System.out.println("something");
          yield "1";
        }
      });
    }
  }
}