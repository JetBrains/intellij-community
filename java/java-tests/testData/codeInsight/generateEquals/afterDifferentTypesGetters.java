import java.util.Arrays;

class A {
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

  public Object[] getA1() {
    return a1;
  }

  public Object[][] getA2() {
    return a2;
  }

  public String[] getA3() {
    return a3;
  }

  public String[][] getA4() {
    return a4;
  }

  public int[] getA5() {
    return a5;
  }

  public int[][] getA6() {
    return a6;
  }

  public byte getA7() {
    return a7;
  }

  public short getA8() {
    return a8;
  }

  public int getA9() {
    return a9;
  }

  public long getA10() {
    return a10;
  }

  public float getA11() {
    return a11;
  }

  public double getA12() {
    return a12;
  }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final A a = (A) o;

        if (getA7() != a.getA7()) return false;
        if (getA8() != a.getA8()) return false;
        if (getA9() != a.getA9()) return false;
        if (getA10() != a.getA10()) return false;
        if (Float.compare(a.getA11(), getA11()) != 0) return false;
        if (Double.compare(a.getA12(), getA12()) != 0) return false;
        // Probably incorrect - comparing Object[] arrays with Arrays.equals
        if (!Arrays.equals(getA1(), a.getA1())) return false;
        if (!Arrays.deepEquals(getA2(), a.getA2())) return false;
        // Probably incorrect - comparing Object[] arrays with Arrays.equals
        if (!Arrays.equals(getA3(), a.getA3())) return false;
        if (!Arrays.deepEquals(getA4(), a.getA4())) return false;
        if (!Arrays.equals(getA5(), a.getA5())) return false;
        if (!Arrays.deepEquals(getA6(), a.getA6())) return false;
        if (a13 != null ? !a13.equals(a.a13) : a.a13 != null) return false;
        if (a14 != null ? !a14.equals(a.a14) : a.a14 != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = getA1() != null ? Arrays.hashCode(getA1()) : 0;
        result = 31 * result + (getA2() != null ? Arrays.deepHashCode(getA2()) : 0);
        result = 31 * result + (getA3() != null ? Arrays.hashCode(getA3()) : 0);
        result = 31 * result + (getA4() != null ? Arrays.deepHashCode(getA4()) : 0);
        result = 31 * result + (getA5() != null ? Arrays.hashCode(getA5()) : 0);
        result = 31 * result + (getA6() != null ? Arrays.deepHashCode(getA6()) : 0);
        result = 31 * result + (int) getA7();
        result = 31 * result + (int) getA8();
        result = 31 * result + getA9();
        result = 31 * result + (int) (getA10() ^ (getA10() >>> 32));
        result = 31 * result + (getA11() != +0.0f ? Float.floatToIntBits(getA11()) : 0);
        temp = Double.doubleToLongBits(getA12());
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + (a13 != null ? a13.hashCode() : 0);
        result = 31 * result + (a14 != null ? a14.hashCode() : 0);
        return result;
    }
}