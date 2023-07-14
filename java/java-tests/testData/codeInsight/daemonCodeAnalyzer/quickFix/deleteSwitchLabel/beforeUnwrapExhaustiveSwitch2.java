// "Remove unreachable branches" "false"

public class Switches {
  sealed interface I2{}
  record A() implements I2{}
  record B() implements I2{}

  void foo(I2 x) {
    if (x instanceof A) {
      System.out.println(switch (x) {
        case A <caret>a  -> {
          System.out.println("something");
          yield "1";
        }
        case B b  -> {
          System.out.println("something");
          yield "2";
        }
      });
    }
  }
}