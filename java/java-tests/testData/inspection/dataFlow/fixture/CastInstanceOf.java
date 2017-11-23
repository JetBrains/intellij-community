import org.jetbrains.annotations.Nullable;
import java.util.*;

class Test {
  Object getXyz() {
    return "foo";
  }

  void test() {
    String x = (String)getXyz();
    if(<warning descr="Condition 'x instanceof String' is redundant and can be replaced with a null check">x instanceof String</warning>) {
      System.out.println("yes!");
    }
  }

  void testUnknown() {
    while(getXyz() != null) {
      String x = (String)getXyz();
      if(<warning descr="Condition 'x instanceof String' is redundant and can be replaced with a null check">x instanceof String</warning>) {
        System.out.println("yes!");
      }
    }
  }

  void testUncheckedCast(List<String> list) {
    List<Integer> l = (List<Integer>)(List<?>)list;
  }

  void testArrayCast(List<String>[] arr) {
    List<Integer>[] arr2 = (List<Integer>[])(List<?>[])arr;
  }

  void testTwoObj(boolean b, Object o1, Object o2) {
    if((b && o1 instanceof String) || o2 instanceof String) {
      String x = (<warning descr="Casting '(b ? o1 : o2)' to 'String' may produce 'java.lang.ClassCastException'">String</warning>)(b ? o1 : o2);
    }
  }

  void method(Object obj) {}

  void testLambdaExpression() {
    method((Runnable) () -> {});
  }
}
