import java.util.Arrays;
import java.util.Objects;

class A {
  int[] a1;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        final A a = (A) o;
        return Objects.deepEquals(a1, a.a1);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(a1);
    }
}