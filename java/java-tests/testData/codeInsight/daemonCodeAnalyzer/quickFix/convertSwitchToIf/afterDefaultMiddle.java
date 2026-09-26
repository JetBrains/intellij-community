// "Replace 'switch' with 'if'" "true-preview"
class X {
  void m(String s) {
      if (s.equals("foo")) {
          System.out.println(1);
      } else if (s.equals("bar")) {
          System.out.println(3);
      } else {
          System.out.println(2);
      }
  }
}