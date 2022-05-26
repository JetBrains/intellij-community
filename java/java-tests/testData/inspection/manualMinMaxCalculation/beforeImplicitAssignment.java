// "Replace with 'Math.min()' call" "true"
class X {
  void test(int a, int b) {
    int c;
    c = a;
    if<caret> (a > b) c = b;
    System.out.println(c);
  }
}