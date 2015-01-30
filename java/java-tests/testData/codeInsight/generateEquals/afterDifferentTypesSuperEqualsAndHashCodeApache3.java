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

        final A a = (A) o;

        return new org.apache.commons.lang3.builder.EqualsBuilder()
                .appendSuper(super.equals(o))
                .append(a7, a.a7)
                .append(a8, a.a8)
                .append(a9, a.a9)
                .append(a10, a.a10)
                .append(a11, a.a11)
                .append(a12, a.a12)
                .append(a1, a.a1)
                .append(a2, a.a2)
                .append(a3, a.a3)
                .append(a4, a.a4)
                .append(a5, a.a5)
                .append(a6, a.a6)
                .append(a13, a.a13)
                .append(a14, a.a14)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new org.apache.commons.lang3.builder.HashCodeBuilder(17, 37)
                .appendSuper(super.hashCode())
                .append(a1)
                .append(a2)
                .append(a3)
                .append(a4)
                .append(a5)
                .append(a6)
                .append(a7)
                .append(a8)
                .append(a9)
                .append(a10)
                .append(a11)
                .append(a12)
                .append(a13)
                .append(a14)
                .toHashCode();
    }
}