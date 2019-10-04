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
}
