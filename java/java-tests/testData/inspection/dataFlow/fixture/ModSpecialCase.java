import java.util.*;

public class ModSpecialCase {
  void test4(int a, int b, int c) {
    if (a >= 0 && a < 1000) {
      if (b >= 0 && b < 1000) {
        if (a < c && a > b && <warning descr="Condition '(a - b) % c == a - b' is always 'true'">(a - b) % c == a - b</warning>) { }
        if (a < c && a > b && <warning descr="Condition '(b - a) % c == b - a' is always 'true'">(b - a) % c == b - a</warning>) { }
      }
    }
  }

  void test3(int a, int b) {
    if (a >= 0 && a < 1000) {
      if (b >= 0 && b < 1000) {
        if (<warning descr="Condition '(a - b) % 1000 != a - b' is always 'false'">(a - b) % 1000 != a - b</warning>) {}
      }
    }
  }

  void test2(String string, String s1, String s2) {
    if (string.isEmpty() || s2.isEmpty()) return;
    long sl = string.length();
    long vc = s1.length();
    long s2l = s2.length();
    long st = vc + s2l;
    if (sl < vc) {
      if (<warning descr="Condition '(sl - vc) % st != 0' is always 'true'">(sl - vc) % st != 0</warning>) {

      }
    }
  }

  void test(String s1, String s2) {
    if (s1.length() < s2.length()) {
      int sz = s1.length() % s2.length();
      if (<warning descr="Condition 'sz == s1.length()' is always 'true'">sz == s1.length()</warning>) {}
    }
  }
}