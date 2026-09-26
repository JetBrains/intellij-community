import java.util.*;

class Test {
  void testCos(double d) {
    if (<warning descr="Condition 'Math.cos(d) == 4.0' is always 'false'">Math.cos(d) == 4.0</warning>) {
      System.out.println("impossible");
    }
    if (<warning descr="Condition 'Math.sin(d) > 2' is always 'false'">Math.sin(d) > 2</warning>) {
      System.out.println("impossible");
    }
    if (<warning descr="Condition 'Math.sin(d) < -1' is always 'false'">Math.sin(d) < -1</warning>) {
      System.out.println("impossible");
    }
    double d1 = Math.sin(d); 
    if (d1 >= 1.0) {
      System.out.println(<warning descr="Condition 'd1 == 1.0' is always 'true'">d1 == 1.0</warning>);
    }
  }
  
  void test() {
    double val = Math.sqrt(2);
    float f = 1.0f;
    double res = val + f;
    if (<warning descr="Condition 'res > 2.41 && res < 2.42' is always 'true'"><warning descr="Condition 'res > 2.41' is always 'true'">res > 2.41</warning> && <warning descr="Condition 'res < 2.42' is always 'true' when reached">res < 2.42</warning></warning>) {}
  }
  
  void testNan() {
    double res = Math.sqrt(Math.sqrt(2) - 2);
    if (<warning descr="Condition 'Double.isNaN(res)' is always 'true'">Double.isNaN(res)</warning>) {}
  }
  
  void testDouble() {
    double d1 = 4;
    double d2 = 5.5;
    if (<warning descr="Condition 'd1 * d2 == 22.0' is always 'true'">d1 * d2 == 22.0</warning>) {}
    if (<warning descr="Condition 'd2 / d1 == 1.375' is always 'true'">d2 / d1 == 1.375</warning>) {}
    if (<warning descr="Condition 'd1 + d2 == 9.5' is always 'true'">d1 + d2 == 9.5</warning>) {}
    if (<warning descr="Condition 'd1 - d2 == -1.5' is always 'true'">d1 - d2 == -1.5</warning>) {}
    if (<warning descr="Condition 'd2 % d1 == 1.5' is always 'true'">d2 % d1 == 1.5</warning>) {}
  }
  
  void testFloat() {
    float d1 = 4f;
    float d2 = 5.5f;
    if (<warning descr="Condition 'd1 * d2 == 18' is always 'false'">d1 * d2 == 18</warning>) {}
    if (<warning descr="Condition 'd2 / d1 == 1.375' is always 'true'">d2 / d1 == 1.375</warning>) {}
    if (<warning descr="Condition 'd1 + d2 == 9.5' is always 'true'">d1 + d2 == 9.5</warning>) {}
    if (<warning descr="Condition 'd1 - d2 == -1.5' is always 'true'">d1 - d2 == -1.5</warning>) {}
    if (<warning descr="Condition 'd2 % d1 == 1.5' is always 'true'">d2 % d1 == 1.5</warning>) {}
  }
}