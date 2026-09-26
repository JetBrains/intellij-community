class Test {
 int i;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        final Test test = (Test) o;
        return i == test.i;
    }

    @Override
    public int hashCode() {
        return i;
    }
}