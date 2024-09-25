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
        if (o == null || getClass() != o.getClass()) return false;

        final A a = (A) o;
        return a7 == a.a7 &&
                a8 == a.a8 &&
                a9 == a.a9 &&
                a10 == a.a10 &&
                Float.compare(a11, a.a11) == 0 &&
                Double.compare(temp, a.temp) == 0 &&
                Arrays.equals(a1, a.a1) &&
                Arrays.deepEquals(a2, a.a2) &&
                Arrays.equals(a3, a.a3) &&
                Arrays.deepEquals(a4, a.a4) &&
                Arrays.equals(a5, a.a5) &&
                Arrays.deepEquals(a6, a.a6) &&
                result.equals(a.result) &&
                a14.equals(a.a14);
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
        result1 = 31 * result1 + a7;
        result1 = 31 * result1 + a8;
        result1 = 31 * result1 + a9;
        result1 = 31 * result1 + (int) (a10 ^ (a10 >>> 32));
        result1 = 31 * result1 + Float.floatToIntBits(a11);
        temp1 = Double.doubleToLongBits(temp);
        result1 = 31 * result1 + (int) (temp1 ^ (temp1 >>> 32));
        result1 = 31 * result1 + result.hashCode();
        result1 = 31 * result1 + a14.hashCode();
        return result1;
    }
}