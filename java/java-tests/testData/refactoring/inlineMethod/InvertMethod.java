
class Test {
  boolean check(String s) {
    if (s == null) return false;
    s = s.trim();
    if (s.isEmpty()) return false;
    return s.length() % 2 == 0;
  }

  boolean use(String s) {
    return !<caret>check(s + s);
  }
}