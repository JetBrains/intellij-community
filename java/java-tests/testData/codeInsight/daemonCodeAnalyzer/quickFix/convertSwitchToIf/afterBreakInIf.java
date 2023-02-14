// "Replace 'switch' with 'if'" "true-preview"
class X {
  void m(String s, boolean r) {
      if (s.equals("a")) {
          System.out.println("a");
          if (r) {
              return;
          }
      }
      System.out.println("d");
  }
}