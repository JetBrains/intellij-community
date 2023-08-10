// "Replace 'switch' with 'if'" "true-preview"
class X {
  void m(String s, boolean r) {
      if (s.equals("a")) {
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