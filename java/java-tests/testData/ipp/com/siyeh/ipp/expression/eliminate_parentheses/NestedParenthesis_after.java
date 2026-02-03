class Test {
  void nestedParenthesisTest(int a, int b, int c) {
      /*1*/
      /*2*/
      /*3*/
      int d = c * a + c * b;
      /*1*/
      /*2*/
      int e = c * (a) + c * b;
      /*1*/
      /*2*/
      /*3*/
      /*4*/
      int f = a - b + c;
      /*1*/
      /*2*/
      /*3*/
      int g = a - (b) + c;
  }
}