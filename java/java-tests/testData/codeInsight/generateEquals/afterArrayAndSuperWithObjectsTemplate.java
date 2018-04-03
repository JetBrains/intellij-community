import java.util.Arrays;

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
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        final A a = (A) o;
        return Arrays.equals(a1, a.a1);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + Arrays.hashCode(a1);
        return result;
    }
}