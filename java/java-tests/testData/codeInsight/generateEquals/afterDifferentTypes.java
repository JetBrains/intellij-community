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


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final A a = (A) o;

        if (a7 != a.a7) return false;
        if (a8 != a.a8) return false;
        if (a9 != a.a9) return false;
        if (a10 != a.a10) return false;
        if (Float.compare(a.a11, a11) != 0) return false;
        if (Double.compare(a.a12, a12) != 0) return false;
        // Probably incorrect - comparing Object[] arrays with Arrays.equals
        if (!Arrays.equals(a1, a.a1)) return false;
        if (!Arrays.deepEquals(a2, a.a2)) return false;
        // Probably incorrect - comparing Object[] arrays with Arrays.equals
        if (!Arrays.equals(a3, a.a3)) return false;
        if (!Arrays.deepEquals(a4, a.a4)) return false;
        if (!Arrays.equals(a5, a.a5)) return false;
        if (!Arrays.deepEquals(a6, a.a6)) return false;
        if (a13 != null ? !a13.equals(a.a13) : a.a13 != null) return false;
        if (a14 != null ? !a14.equals(a.a14) : a.a14 != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = Arrays.hashCode(a1);
        result = 31 * result + Arrays.deepHashCode(a2);
        result = 31 * result + Arrays.hashCode(a3);
        result = 31 * result + Arrays.deepHashCode(a4);
        result = 31 * result + Arrays.hashCode(a5);
        result = 31 * result + Arrays.deepHashCode(a6);
        result = 31 * result + (int) a7;
        result = 31 * result + (int) a8;
        result = 31 * result + a9;
        result = 31 * result + (int) (a10 ^ (a10 >>> 32));
        result = 31 * result + (a11 != +0.0f ? Float.floatToIntBits(a11) : 0);
        temp = Double.doubleToLongBits(a12);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + (a13 != null ? a13.hashCode() : 0);
        result = 31 * result + (a14 != null ? a14.hashCode() : 0);
        return result;
    }
}