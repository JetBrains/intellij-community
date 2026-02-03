import java.util.Arrays;
import java.util.Objects;

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
        if (o == null || getClass() != o.getClass()) return false;

        final A a = (A) o;
        return a7 == a.a7 &&
                a8 == a.a8 &&
                a9 == a.a9 &&
                a10 == a.a10 &&
                Float.compare(a11, a.a11) == 0 &&
                Double.compare(a12, a.a12) == 0 &&
                Arrays.equals(a1, a.a1) &&
                Arrays.deepEquals(a2, a.a2) &&
                Arrays.equals(a3, a.a3) &&
                Arrays.deepEquals(a4, a.a4) &&
                Arrays.equals(a5, a.a5) &&
                Arrays.deepEquals(a6, a.a6) &&
                Objects.equals(a13, a.a13) &&
                Objects.equals(a14, a.a14);
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
        result = 31 * result + a7;
        result = 31 * result + a8;
        result = 31 * result + a9;
        result = 31 * result + (int) (a10 ^ (a10 >>> 32));
        result = 31 * result + Float.floatToIntBits(a11);
        temp = Double.doubleToLongBits(a12);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + Objects.hashCode(a13);
        result = 31 * result + Objects.hashCode(a14);
        return result;
    }
}