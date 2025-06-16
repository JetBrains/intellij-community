// "Convert to record class" "true-preview"

import java.util.Objects;

class <caret>R {
  private final Object a;
  private final String b;
  private final int c;
  private final double d;
  private final float e;
  private final int[] arr;

  R(Object a, String b, int c, double d, float e, int[] arr) {
    this.a = a;
    this.b = b;
    this.c = c;
    this.d = d;
    this.e = e;
    this.arr = arr;
  }

  public Object a() { return a; }

  public String b() { return b; }

  public int c() { return c; }

  public double d() { return d; }

  public float e() { return e; }

  public int[] arr() { return arr; }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (R)obj;
    return Objects.equals(this.a, that.a) &&
           Objects.equals(this.b, that.b) &&
           this.c == that.c &&
           Double.doubleToLongBits(this.d) == Double.doubleToLongBits(that.d) &&
           Float.floatToIntBits(this.e) == Float.floatToIntBits(that.e) &&
           Objects.equals(this.arr, that.arr);
  }

  @Override
  public int hashCode() {
    return Objects.hash(a, b, c, d, e, arr);
  }

  @Override
  public String toString() {
    return "R[" +
           "a=" + a + ", " +
           "b=" + b + ", " +
           "c=" + c + ", " +
           "d=" + d + ", " +
           "e=" + e + ", " +
           "arr=" + arr + ']';
  }
}
