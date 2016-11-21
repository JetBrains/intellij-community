class Test {
 int i;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final Test test = (Test) o;

        if (i != test.i) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return i;
    }
}