// "Unroll loop" "true"
class Test {
  void test(String s1, String s2, String s3) {
      if ((i += s1.length()) <= 10) {
          if ((i += s2.length()) <= 10) {
              if ((i += s3.length()) <= 10) {
              }
          }
      }
      System.out.println(i);
  }

  void foo(boolean b) {}
}