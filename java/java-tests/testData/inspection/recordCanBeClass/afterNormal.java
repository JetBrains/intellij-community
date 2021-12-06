// "Convert record to class" "true"

import java.util.Objects;

/**
 * Cool record.  
 */
final class R {
    private final int a;
    private final boolean b;
    private final float c;
    private final double d;
    private final String s;

    /**
     * @param a a value
     *          (multiline)
     * @param b b value
     * @param c c value
     * @param d d value
     * @param s s value
     */
    R(int a, boolean b, float c, double d, String s) {
        this.a = a;
        this.b = b;
        this.c = c;
        this.d = d;
        this.s = s;
    }

    public int a() {
        return a;
    }

    public boolean b() {
        return b;
    }

    public float c() {
        return c;
    }

    public double d() {
        return d;
    }

    public String s() {
        return s;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (R) obj;
        return this.a == that.a &&
                this.b == that.b &&
                Float.floatToIntBits(this.c) == Float.floatToIntBits(that.c) &&
                Double.doubleToLongBits(this.d) == Double.doubleToLongBits(that.d) &&
                Objects.equals(this.s, that.s);
    }

    @Override
    public int hashCode() {
        return Objects.hash(a, b, c, d, s);
    }

    @Override
    public String toString() {
        return "R[" +
                "a=" + a + ", " +
                "b=" + b + ", " +
                "c=" + c + ", " +
                "d=" + d + ", " +
                "s=" + s + ']';
    }
}