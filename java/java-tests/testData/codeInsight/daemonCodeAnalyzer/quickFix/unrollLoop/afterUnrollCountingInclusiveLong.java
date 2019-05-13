// "Unroll loop" "true"
class Test {
  void test() {
      if (0L % 7 != 6) {
          System.out.println("Hi!" + 0L);
          if (1L % 7 != 6) {
              System.out.println("Hi!" + 1L);
              if (2L % 7 != 6) {
                  System.out.println("Hi!" + 2L);
                  if (3L % 7 != 6) {
                      System.out.println("Hi!" + 3L);
                      if (4L % 7 != 6) {
                          System.out.println("Hi!" + 4L);
                          if (5L % 7 != 6) {
                              System.out.println("Hi!" + 5L);
                              if (6L % 7 != 6) {
                                  System.out.println("Hi!" + 6L);
                                  if (7L % 7 != 6) {
                                      System.out.println("Hi!" + 7L);
                                      if (8L % 7 != 6) {
                                          System.out.println("Hi!" + 8L);
                                          if (9L % 7 != 6) {
                                              System.out.println("Hi!" + 9L);
                                              if (10L % 7 != 6) {
                                                  System.out.println("Hi!" + 10L);
                                              }
                                          }
                                      }
                                  }
                              }
                          }
                      }
                  }
              }
          }
      }
  }
}