class Integer {
    int i;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final Integer integer = (Integer) o;

        if (i != integer.i) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return i;
    }
}