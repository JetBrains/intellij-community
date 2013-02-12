class Test {
    int i;
    Test a;
    Test b;

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final Test test = (Test) o;

        if (i != test.i) return false;
        if (!a.equals(test.a)) return false;
        if (b != null ? !b.equals(test.b) : test.b != null) return false;

        return true;
    }

    public int hashCode() {
        return 0;
    }
}