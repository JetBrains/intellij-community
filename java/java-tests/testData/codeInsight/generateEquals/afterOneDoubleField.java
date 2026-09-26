class Test {
    double d;

    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        final Test test = (Test) o;
        return Double.compare(d, test.d) == 0;
    }

    public int hashCode() {
        final long temp = Double.doubleToLongBits(d);
        return (int) (temp ^ (temp >>> 32));
    }
}