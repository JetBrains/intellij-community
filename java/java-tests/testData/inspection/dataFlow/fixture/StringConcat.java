import java.util.*;

public class StringConcat {
  void test(String s1, String s2) {
    if (s1.equals("foo") && s2.equals("bar")) {
      String res = s1 + s2;
      if (<warning descr="Condition 'res.equals(\"foobar\")' is always 'true'">res.equals("foobar")</warning>) {

      }
    }
    if (s1.startsWith("foo") && s2.startsWith("bar")) {
      String res = s1 + s2;
      if (<warning descr="Condition 'res.length() < 6' is always 'false'">res.length() < 6</warning>) {}
    }
  }

  void testConcatBoolean(boolean b) {
    String str;
    if (b) {
      str = "Value: " + b;
      if (<warning descr="Condition 'str.equals(\"Value: true\")' is always 'true'">str.equals("Value: true")</warning>) {

      }
    }

    str = b+":";
    if (<warning descr="Condition 'str.length() == 5 || str.length() == 6' is always 'true'">str.length() == 5 || <warning descr="Condition 'str.length() == 6' is always 'true' when reached">str.length() == 6</warning></warning>) {}
  }

  void testConcat(int a, long b) {
    String s = a + ":" + b;
    if (<warning descr="Condition 's.length() >= 3 && s.length() <= 32' is always 'true'"><warning descr="Condition 's.length() >= 3' is always 'true'">s.length() >= 3</warning> && <warning descr="Condition 's.length() <= 32' is always 'true' when reached">s.length() <= 32</warning></warning>) {}
    if (a > 0 && a < 10) {
      String s1 = "Value: " + a;
      if (<warning descr="Condition 's1.length() == 8' is always 'true'">s1.length() == 8</warning>) {}
      if (b > -200 && b < -100) {
        s1 += b;
        if (<warning descr="Condition 's1.length() == 12' is always 'true'">s1.length() == 12</warning>) {}
      }
    }
    if (b >= Integer.MIN_VALUE && b <= Integer.MAX_VALUE) {
      String bs = Long.toString(b);
      if (<warning descr="Condition 'bs.isEmpty()' is always 'false'">bs.isEmpty()</warning>) {}
      if (<warning descr="Condition 'bs.length() >= 1 && bs.length() <= 11' is always 'true'"><warning descr="Condition 'bs.length() >= 1' is always 'true'">bs.length() >= 1</warning> && <warning descr="Condition 'bs.length() <= 11' is always 'true' when reached">bs.length() <= 11</warning></warning>) {
      }
    }
    if (a == 10) {
      String as = "Value: " + a;
      if (<warning descr="Condition 'as.equals(\"Value: 10\")' is always 'true'">as.equals("Value: 10")</warning>) {

      }
    }
  }

  void testConcatFloat(float a, double b, String s2) {
    if (a == 1) {
      String s = a+":";
      if (s.equals("1.0:")) {}
    }
    String s1 = a + ":" + b;
    if (<warning descr="Condition 's1.length() >= 7 && s1.length() <= 53' is always 'true'"><warning descr="Condition 's1.length() >= 7' is always 'true'">s1.length() >= 7</warning> && <warning descr="Condition 's1.length() <= 53' is always 'true' when reached">s1.length() <= 53</warning></warning>) {

    }
    String s3 = s2 + b + a;
    if (<warning descr="Condition 's3.isEmpty()' is always 'false'">s3.isEmpty()</warning>) {}
    if (<warning descr="Condition 's3.length() < 6' is always 'false'">s3.length() < 6</warning>) {}
  }

}
