import java.util.Objects;

class X {
  private String s = null;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        final X x = (X) o;
        return Objects.equals(s, x.s);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(s);
    }
}