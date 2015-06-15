import java.util.Arrays;

class A {
  int[] a1;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final A a = (A) o;
        return Arrays.equals(a1, a.a1);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(a1);
    }
}