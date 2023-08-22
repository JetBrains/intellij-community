// "Remove unreachable branches" "false"

public class Switches {
  sealed interface I2{}
  enum En implements I2{A, B,}
  void foo(I2 x) {
    if (x == En.A) {
      System.out.println(switch (x) {
        case En.A<caret>  -> {
          System.out.println("something");
          yield "1";
        }
        case En.B ->{
          yield "2";
        }
      });
    }
  }
}