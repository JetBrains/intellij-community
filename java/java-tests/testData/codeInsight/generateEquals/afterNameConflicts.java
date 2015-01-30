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
  double temp;

  Object result;
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
        if (Double.compare(a.temp, temp) != 0) return false;
        // Probably incorrect - comparing Object[] arrays with Arrays.equals
        if (!Arrays.equals(a1, a.a1)) return false;
        if (!Arrays.deepEquals(a2, a.a2)) return false;
        // Probably incorrect - comparing Object[] arrays with Arrays.equals
        if (!Arrays.equals(a3, a.a3)) return false;
        if (!Arrays.deepEquals(a4, a.a4)) return false;
        if (!Arrays.equals(a5, a.a5)) return false;
        if (!Arrays.deepEquals(a6, a.a6)) return false;
        if (!result.equals(a.result)) return false;
        if (!a14.equals(a.a14)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result1;
        long temp1;
        result1 = Arrays.hashCode(a1);
        result1 = 31 * result1 + Arrays.deepHashCode(a2);
        result1 = 31 * result1 + Arrays.hashCode(a3);
        result1 = 31 * result1 + Arrays.deepHashCode(a4);
        result1 = 31 * result1 + Arrays.hashCode(a5);
        result1 = 31 * result1 + Arrays.deepHashCode(a6);
        result1 = 31 * result1 + (int) a7;
        result1 = 31 * result1 + (int) a8;
        result1 = 31 * result1 + a9;
        result1 = 31 * result1 + (int) (a10 ^ (a10 >>> 32));
        result1 = 31 * result1 + (a11 != +0.0f ? Float.floatToIntBits(a11) : 0);
        temp1 = Double.doubleToLongBits(temp);
        result1 = 31 * result1 + (int) (temp1 ^ (temp1 >>> 32));
        result1 = 31 * result1 + result.hashCode();
        result1 = 31 * result1 + a14.hashCode();
        return result1;
    }
}