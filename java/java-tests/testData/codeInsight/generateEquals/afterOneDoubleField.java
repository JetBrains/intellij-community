class Test {
    double d;

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final Test test = (Test) o;

        if (Double.compare(test.d, d) != 0) return false;

        return true;
    }

    public int hashCode() {
        final long temp = d != +0.0d ? Double.doubleToLongBits(d) : 0L;
        return (int) (temp ^ (temp >>> 32));
    }
}