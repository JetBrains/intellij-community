// "Replace 'switch' with 'if'" "true-preview"
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