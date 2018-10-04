// "Replace 'switch' with 'if'" "true"
class X {
  int m(String s, boolean r) {
    if (r) return 1;
    else if ("a".equals(s)) {
        return 1;
    } else if ("b".equals(s)) {
        return 2;
    } else {
        return 3;
    }
  }
}