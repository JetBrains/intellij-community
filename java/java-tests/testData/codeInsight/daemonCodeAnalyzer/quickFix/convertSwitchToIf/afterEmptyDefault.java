// "Replace 'switch' with 'if'" "true-preview"
class X {
  int m(String s, boolean r) {
      //ignore
      if (s.equals("x")) {
          System.out.println("foo");
      }
  }
}