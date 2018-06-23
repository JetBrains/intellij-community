import java.util.Arrays;
import java.util.Objects;

class A {
  int i;
  String s;
  int[] a1;
  int[] a2;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final A a = (A) o;
        return i == a.i &&
                Objects.equals(s, a.s) &&
                Arrays.equals(a1, a.a1) &&
                Arrays.equals(a2, a.a2);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(i, s);
        result = 31 * result + Arrays.hashCode(a1);
        result = 31 * result + Arrays.hashCode(a2);
        return result;
    }
}