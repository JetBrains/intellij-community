import java.util.*;

public class RelationsOnAddition {
  void test(int a, int b) {
    if (a <= 0 || a > 1000) return;
    if (b <= 0 || b > 1000) return;
    int c = a + b;
    if (<warning descr="Condition 'c > a' is always 'true'">c > a</warning>) {}
    if (<warning descr="Condition 'c > b' is always 'true'">c > b</warning>) {}
  }
  
  void test1(String s1, String s2) {
    if (s1.isEmpty()) return;
    int sum = s1.length() + s2.length();
    // may overflow
    if (sum < s2.length()) {}
    if (<warning descr="Condition 'sum == s2.length()' is always 'false'">sum == s2.length()</warning>) {}
  }
}