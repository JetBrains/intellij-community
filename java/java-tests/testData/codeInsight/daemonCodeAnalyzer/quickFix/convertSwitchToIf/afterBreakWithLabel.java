// "Replace 'switch' with 'if'" "true-preview"
class X {
  int m(String s, int x) {
    if (x > 0) {
      SWITCH:
      {
          if (s.equals("a")) {
              System.out.println("a");
              for (int i = 0; i < 10; i++) {
                  System.out.println(i);
                  if (i == x) return 0;
                  if (i == x * 2) break;
              }
          }
          System.out.println("d");
      }
    } else {
      return 1;
    }
    return 0;
  }
}