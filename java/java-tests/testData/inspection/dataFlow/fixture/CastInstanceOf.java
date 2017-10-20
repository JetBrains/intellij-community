import org.jetbrains.annotations.Nullable;
import java.util.*;

class Test {
  Object getXyz() {
    return "foo";
  }

  void test() {
    String x = (String)getXyz();
    if(<warning descr="Condition 'x instanceof String' is redundant and can be replaced with '!= null'">x instanceof String</warning>) {
      System.out.println("yes!");
    }
  }

  void testUnknown() {
    while(getXyz() != null) {
      String x = (String)getXyz();
      if(<warning descr="Condition 'x instanceof String' is redundant and can be replaced with '!= null'">x instanceof String</warning>) {
        System.out.println("yes!");
      }
    }
  }
}
