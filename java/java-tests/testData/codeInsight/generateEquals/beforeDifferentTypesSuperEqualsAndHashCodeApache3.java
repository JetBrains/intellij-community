class B {
  @Override
  public boolean equals(Object obj) {
    return obj != null;
  }

  @Override
  public int hashCode() {
    return super.hashCode();
  }
}
class A extends B {
  Object[] a1;
  Object[][] a2;
  String[] a3;
  String[][] a4;
  int[] a5;
  int[][] a6;

  byte a7;
  short a8;
  int a9;
  long a10;
  float a11;
  double a12;

  Object a13;
  String a14;

  <caret>
}