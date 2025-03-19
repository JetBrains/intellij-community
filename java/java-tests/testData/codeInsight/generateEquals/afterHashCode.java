import java.util.Objects;

class Test {
    int i;
    Test a;
    Test b;
    double c;

    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        final Test test = (Test) o;
        return i == test.i &&
                Double.compare(c, test.c) == 0 &&
                a.equals(test.a) &&
                Objects.equals(b, test.b);
    }

    public int hashCode() {
        int result;
        long temp;
        result = i;
        result = 31 * result + a.hashCode();
        result = 31 * result + Objects.hashCode(b);
        temp = Double.doubleToLongBits(c);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result;
    }
}