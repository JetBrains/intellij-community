class Test {
  void distributiveTest(int a, int b, int c, int d, double cc) {
      /*2*/
      int e = /*1*/a * b + a * c;
      /*1*/
      /*2*/
      /*3*/
      int f = a * b + a * c;
    int g = /*1*/a * b * d + a * c * d;
      /*1*/
      int h = a / d * b + a / d * c;
    int i = a / d * b + a / d * c;
      /*1*/
      int j = a - b * c - b * d;
    int k = c * b + d * b;
    int n = a & c | a & b;
      /*1*/
      /*2*/
      int o = 2 * 3 + 2 * a / b * c;
      /*1*/
      /*2*/
      double p = a / cc + b / cc;
  }

  void distributiveBooleanTest(boolean a, boolean b, boolean c, boolean d, int i1, int i2) {
    boolean g = a && b || a && c;
    boolean i = !a && b && !d || !a && !c && !d;
    boolean j = a && !b && c || a && !d;
    boolean k = d || a && b || a && c;
    boolean l = d ^ a && b || d ^ a && c;
    boolean m = a && i1 == 1 || a && b;
    boolean n = a && i1 != 1 || a && i2 >= 1;
    boolean o = a && i1 > 1 && a == true || a && i2 <= 1 && a == true;
    boolean p = a && i1 < 1 || a && b;
  }

  void formatTest(String foo, String bar) {
      boolean x = !foo.equals("%s") && foo.equals(" %s ") || !foo.equals("%s") && bar.equals("%s");
  }
}