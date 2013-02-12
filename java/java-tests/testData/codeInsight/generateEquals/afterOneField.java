class Test {
    Object d;

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final Test test = (Test) o;

        if (d != null ? !d.equals(test.d) : test.d != null) return false;

        return true;
    }

    public int hashCode() {
        return d != null ? d.hashCode() : 0;
    }
}