import java.util.Arrays;
import java.util.Objects;

class I {
  public int hashCode() {
    return 0;
  }
  public boolean equals(Object o) {
    return o == this;
  }
}
class A extends I {
  int[] a1;


    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        final A a = (A) o;
        return Objects.deepEquals(a1, a.a1);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), Arrays.hashCode(a1));
    }
}