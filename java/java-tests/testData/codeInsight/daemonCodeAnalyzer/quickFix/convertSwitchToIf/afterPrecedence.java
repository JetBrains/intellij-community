// "Replace 'switch' with 'if'" "true-preview"
class X {
  void test(int i) {
      if (("1" + (--i)).equals("2")) {
          System.out.println("2");
      } else {
          System.out.println("1");
      }
  }
}