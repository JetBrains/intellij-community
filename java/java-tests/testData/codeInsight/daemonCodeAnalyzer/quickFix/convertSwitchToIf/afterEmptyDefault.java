// "Replace 'switch' with 'if'" "true"
class X {
  int m(String s, boolean r) {
      //ignore
      if ("x".equals(s)) {
          System.out.println("foo");
      }
  }
}