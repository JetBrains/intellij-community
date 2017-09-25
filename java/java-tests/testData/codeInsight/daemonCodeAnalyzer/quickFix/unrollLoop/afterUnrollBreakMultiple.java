// "Unroll loop" "true"
class Test {
  void test(String s1, String s2, String s3) {
      if (s1.length() <= 5) {
          System.out.println("Long string: " + s1);
          if (s1.length() <= 20) {
              System.out.println("Very long string: " + s1);
              if (s2.length() <= 5) {
                  System.out.println("Long string: " + s2);
                  if (s2.length() <= 20) {
                      System.out.println("Very long string: " + s2);
                      if (s3.length() <= 5) {
                          System.out.println("Long string: " + s3);
                          if (s3.length() <= 20) {
                              System.out.println("Very long string: " + s3);
                          }
                      }
                  }
              }
          }
      }
  }

  void foo(boolean b) {}
}