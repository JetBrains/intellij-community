// "Replace with lambda" "true"
class Test {
  interface I {

  }
  interface Bar extends I {
    int compare(String o1, String o2);
  }
  {
    I bar2 = (Bar) (o1, o2) -> 0;
  }
}