import java.util.*;

class Test {

  public void test(String s, String s1) {
    int pos = s.indexOf('x');
    if (<warning descr="Condition 'pos < s.length()' is always 'true'">pos < s.length()</warning>) {}
    int pos2 = s.indexOf(s1);
    if (pos2 < s.length()) {}
    int pos3 = s.lastIndexOf('x');
    if (<warning descr="Condition 'pos3 < s.length()' is always 'true'">pos3 < s.length()</warning>) {}
    int pos4 = s.lastIndexOf("xyz");
    if (<warning descr="Condition 'pos4 < s.length()' is always 'true'">pos4 < s.length()</warning>) {}
    int pos5 = s.lastIndexOf(s1);
    if (pos5 < s.length()) {}
    if (<warning descr="Condition 'pos5 <= s.length()' is always 'true'">pos5 <= s.length()</warning>) {}

    if (pos != -1 && <warning descr="Condition '!s.isEmpty()' is always 'true' when reached">!s.isEmpty()</warning>) {}
    if (s.isEmpty()) {
      if (<warning descr="Condition 'pos == -1' is always 'true'">pos == -1</warning>) {}
    }
    int max = Math.<warning descr="Result of 'max' is the same as the first argument making the call meaningless">max</warning>(s.length(), s.indexOf('a'));
  }

  private static void foo(String text) {
    String s = "";
    int idx = text.indexOf(s);
    if (idx < 0) {
      return;
    }
    String text1 = text.substring(idx);
    System.out.println(text1.isEmpty());
  }
}