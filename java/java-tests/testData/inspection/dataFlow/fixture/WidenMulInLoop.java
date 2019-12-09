import java.util.*;

public class WidenMulInLoop {
  void test1() {
    for (int i = 0; <warning descr="Condition 'i < Integer.MAX_VALUE' is always 'true'">i < Integer.MAX_VALUE</warning>; <warning descr="Variable update does nothing">i</warning> *= 3) {
      System.out.println(i);
    }
  }

  void test2() {
    for (int i = 1; <warning descr="Condition 'i < Integer.MAX_VALUE' is always 'true'">i < Integer.MAX_VALUE</warning>; i *= 2) {
      System.out.println(i);
    }
  }

  void test3() {
    for (int i = 1; i < Integer.MAX_VALUE; i *= 3) {
      System.out.println(i);
    }
  }
}
