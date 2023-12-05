class Test {
    float d;

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final Test test = (Test) o;

        if (Float.compare(d, test.d) != 0) return false;

        return true;
    }

    public int hashCode() {
        return Float.floatToIntBits(d);
    }
}