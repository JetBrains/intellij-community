class Arrays {
  int[] i;

    @java.lang.Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final Arrays arrays = (Arrays) o;

        if (!java.util.Arrays.equals(i, arrays.i)) return false;

        return true;
    }

    @java.lang.Override
    public int hashCode() {
        return i != null ? i.hashCode() : 0;
    }
}