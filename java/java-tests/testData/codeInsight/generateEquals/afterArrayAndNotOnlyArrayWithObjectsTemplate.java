import java.util.Arrays;
import java.util.Objects;

class A {
  int i;
  String s;
  int[] a1;
  int[] a2;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        final A a = (A) o;
        return i == a.i &&
                Objects.equals(s, a.s) &&
                Objects.deepEquals(a1, a.a1) &&
                Objects.deepEquals(a2, a.a2);
    }

    @Override
    public int hashCode() {
        return Objects.hash(i, s, Arrays.hashCode(a1), Arrays.hashCode(a2));
    }
}