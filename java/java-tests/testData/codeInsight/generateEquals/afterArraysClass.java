class Arrays {
  int[] i;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final Arrays arrays = (Arrays) o;
        return java.util.Arrays.equals(i, arrays.i);
    }

    @Override
    public int hashCode() {
        return java.util.Arrays.hashCode(i);
    }
}