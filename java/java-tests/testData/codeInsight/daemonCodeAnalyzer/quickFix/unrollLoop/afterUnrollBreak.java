// "Unroll loop" "true-preview"
class Test {
  void test() {
      if (!(Math.random() > 0.5)) {
          System.out.println((Object) "one");
          if (!(Math.random() > 0.5)) {
              System.out.println((Object) 1);
              if (!(Math.random() > 0.5)) {
                  System.out.println((Object) 1.0);
                  if (!(Math.random() > 0.5)) {
                      System.out.println((Object) 1.0f);
                  }
              }
          }
      }//Comment
  }

  void foo(boolean b) {}
}