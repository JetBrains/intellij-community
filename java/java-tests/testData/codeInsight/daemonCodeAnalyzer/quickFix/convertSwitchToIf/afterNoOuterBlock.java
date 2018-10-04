// "Replace 'switch' with 'if'" "true"
class X {
  void m(String s, boolean r) {
    if (r) {
        if ("a".equals(s)) {
            System.out.println("a");
        }
        System.out.println("d");
    }
  }
}