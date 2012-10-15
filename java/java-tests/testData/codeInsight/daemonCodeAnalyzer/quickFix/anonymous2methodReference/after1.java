// "Replace with method reference" "true"
class Test {
  interface Bar {
    int compare(String o1, String o2);
  }
    static int c(String o1, String o2) {
        return 0;
    }

  {
    Bar bar2 = Test::c;
  }
}