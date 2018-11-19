// "Replace 'switch' with 'if'" "true"
class X {
  void m(String s, boolean r) {
      if ("a".equals(s)) {
          System.out.println("a");
          if (r) {
          } else {
              throw new RuntimeException();
          }
      } else {
          System.out.println("d");
      }
  }
}