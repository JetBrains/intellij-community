// "Replace 'switch' with 'if'" "true-preview"
class X {
  int m(String s, boolean r) {
    if (r) return 1;
    else if (s.equals("a")) {
        return 1;
    } else if (s.equals("b")) {
        return 2;
    } else {
        return 3;
    }
  }
}