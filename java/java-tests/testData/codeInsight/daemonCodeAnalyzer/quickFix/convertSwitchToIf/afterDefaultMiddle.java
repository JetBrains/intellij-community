// "Replace 'switch' with 'if'" "true"
class X {
  void m(String s) {
      if ("foo".equals(s)) {
          System.out.println(1);
      } else if ("bar".equals(s)) {
          System.out.println(3);
      } else {
          System.out.println(2);
      }
  }
}