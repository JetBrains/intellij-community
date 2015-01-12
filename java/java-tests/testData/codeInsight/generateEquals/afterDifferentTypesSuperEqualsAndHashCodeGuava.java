import java.util.Arrays;

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


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        final A a = (A) o;
        return com.google.common.base.Objects.equal(a10, a.a10) &&
                com.google.common.base.Objects.equal(a11, a.a11) &&
                com.google.common.base.Objects.equal(a12, a.a12) &&
                com.google.common.base.Objects.equal(a7, a.a7) &&
                com.google.common.base.Objects.equal(a8, a.a8) &&
                com.google.common.base.Objects.equal(a9, a.a9) &&
                Arrays.equals(a1, a.a1) &&
                com.google.common.base.Objects.equal(a13, a.a13) &&
                com.google.common.base.Objects.equal(a14, a.a14) &&
                Arrays.deepEquals(a2, a.a2) &&
                Arrays.equals(a3, a.a3) &&
                Arrays.deepEquals(a4, a.a4) &&
                Arrays.equals(a5, a.a5) &&
                Arrays.deepEquals(a6, a.a6);
    }

    @Override
    public int hashCode() {
        return com.google.common.base.Objects.hashCode(super.hashCode(), a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14);
    }
}