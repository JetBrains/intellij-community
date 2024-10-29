class Test {
    float d;

    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        final Test test = (Test) o;
        return Float.compare(d, test.d) == 0;
    }

    public int hashCode() {
        return Float.floatToIntBits(d);
    }
}