import java.math.BigInteger;
import java.util.function.Function;

class Test {
  interface Base {
    void baseMethod();
  }

  interface A1 extends Base {}
  interface A2 extends Base {}
  interface B1 extends A1 {}
  interface B2 extends A2 {}
  interface C1 extends B1 {}

  long test(Object obj) {
    if (obj instanceof C1 || obj instanceof A2) {
      obj.baseMe<caret>
    }
    return -1;
  }
}