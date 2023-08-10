// "Replace 'switch' with 'if'" "true-preview"
class X {
  void m(String s, boolean r) {
    if (r) {
        if (s.equals("a")) {
            System.out.println("a");
        }
        System.out.println("d");
    }
  }
}