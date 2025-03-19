import java.util.Objects;

class Test {
    Object d;

    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        final Test test = (Test) o;
        return Objects.equals(d, test.d);
    }

    public int hashCode() {
        return Objects.hashCode(d);
    }
}