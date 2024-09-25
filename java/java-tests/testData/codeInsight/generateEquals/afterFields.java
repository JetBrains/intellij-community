import java.util.Objects;

class Test {
    int i;
    Test a;
    Test b;

    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        final Test test = (Test) o;
        return i == test.i && a.equals(test.a) && Objects.equals(b, test.b);
    }

    public int hashCode() {
        return 0;
    }
}