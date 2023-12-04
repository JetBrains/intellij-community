class Integer {
    int i;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final Integer integer = (Integer) o;
        return i == integer.i;
    }

    @Override
    public int hashCode() {
        return i;
    }
}