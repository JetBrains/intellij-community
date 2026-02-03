import java.util.Arrays;
import java.util.Objects;

class X {
  private String[][] s = null;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        final X x = (X) o;
        return Objects.deepEquals(s, x.s);
    }

    @Override
    public int hashCode() {
        return Arrays.deepHashCode(s);
    }
}