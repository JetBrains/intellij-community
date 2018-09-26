// "Replace 'switch' with 'if'" "true"
class X {
  int m(String s, boolean r) {
      if ("a".equals(s)) {
          return 1;
      } else if ("b".equals(s)) {
          return 2;
      }
      return 3;
  }
}