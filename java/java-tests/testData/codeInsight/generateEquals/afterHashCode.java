class Test {
    int i;
    Test a;
    Test b;
    double c;

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final Test test = (Test) o;

        if (i != test.i) return false;
        if (Double.compare(test.c, c) != 0) return false;
        if (!a.equals(test.a)) return false;
        if (b != null ? !b.equals(test.b) : test.b != null) return false;

        return true;
    }

    public int hashCode() {
        int result;
        long temp;
        result = i;
        result = 31 * result + a.hashCode();
        result = 31 * result + (b != null ? b.hashCode() : 0);
        temp = Double.doubleToLongBits(c);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result;
    }
}