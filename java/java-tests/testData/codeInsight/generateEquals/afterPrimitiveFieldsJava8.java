public class X {
  private long l = 1l;
  private boolean b = true;
  private byte v = 1;
  private short s = 1;
  private int i = 1;
  private float f = 1.0f;
  private double d = 1.0;

    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        final X x = (X) o;
        return l == x.l &&
                b == x.b &&
                v == x.v &&
                s == x.s &&
                i == x.i &&
                Float.compare(f, x.f) == 0 &&
                Double.compare(d, x.d) == 0;
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