import java.util.Arrays;

class A<T extends String, K> {
  Object[] a1;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final A<?, ?> a = (A<?, ?>) o;

        // Probably incorrect - comparing Object[] arrays with Arrays.equals
        if (!Arrays.equals(a1, a.a1)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(a1);
    }
}