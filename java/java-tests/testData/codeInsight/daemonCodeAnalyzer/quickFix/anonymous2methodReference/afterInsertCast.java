// "Replace with method reference" "true"
class Test {
  interface I {}
  interface Bar extends I {
    int compare(String o1, String o2);
  }
    static int c(String o1, String o2) {
        return 0;
    }

  {
    I bar2 = (Bar) Test::c;
  }
}