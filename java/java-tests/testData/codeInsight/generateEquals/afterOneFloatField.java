class Test {
    float d;

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final Test test = (Test) o;

        if (Float.compare(test.d, d) != 0) return false;

        return true;
    }

    public int hashCode() {
        return (d != +0.0f ? Float.floatToIntBits(d) : 0);
    }
}