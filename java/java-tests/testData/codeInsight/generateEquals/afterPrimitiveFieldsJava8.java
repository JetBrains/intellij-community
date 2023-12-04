public class X {
  private long l = 1l;
  private boolean b = true;
  private byte v = 1;
  private short s = 1;
  private int i = 1;
  private float f = 1.0f;
  private double d = 1.0;

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final X x = (X) o;

        if (l != x.l) return false;
        if (b != x.b) return false;
        if (v != x.v) return false;
        if (s != x.s) return false;
        if (i != x.i) return false;
        if (Float.compare(f, x.f) != 0) return false;
        if (Double.compare(d, x.d) != 0) return false;

        return true;
    }

    public int hashCode() {
        int result = Long.hashCode(l);
        result = 31 * result + Boolean.hashCode(b);
        result = 31 * result + v;
        result = 31 * result + s;
        result = 31 * result + i;
        result = 31 * result + Float.hashCode(f);
        result = 31 * result + Double.hashCode(d);
        return result;
    }
}