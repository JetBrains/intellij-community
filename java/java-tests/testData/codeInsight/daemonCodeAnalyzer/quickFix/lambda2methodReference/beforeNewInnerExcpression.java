// "Replace lambda with method reference" "false"
import java.util.function.*;

class Outer {
  private static Outer outer = new Outer();

  private static void test() {
    Function<String, InstanceClass> supplier = s -> outer.n<caret>ew Inner(s);
  }

  class Inner {
    Inner(String s) { }
  }
}

