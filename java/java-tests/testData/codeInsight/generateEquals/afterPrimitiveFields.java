public class Test {
    int f;
    public int j;
    int h;

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final Test test = (Test) o;

        if (f != test.f) return false;
        if (j != test.j) return false;
        if (h != test.h) return false;

        return true;
    }

    public int hashCode() {
        return 0;
    }
}