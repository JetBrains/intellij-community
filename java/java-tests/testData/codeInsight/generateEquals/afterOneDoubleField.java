class Test {
    double d;

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final Test test = (Test) o;

        if (Double.compare(d, test.d) != 0) return false;

        return true;
    }

    public int hashCode() {
        final long temp = Double.doubleToLongBits(d);
        return (int) (temp ^ (temp >>> 32));
    }
}