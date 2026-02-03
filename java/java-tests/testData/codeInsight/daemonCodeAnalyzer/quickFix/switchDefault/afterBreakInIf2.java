// "Unwrap 'switch'" "true-preview"
class X {
  String test(char c) {
      if (c == 'a') {
          System.out.println("foo");
      }
      System.out.println("oops");
  }
}