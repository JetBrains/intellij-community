public class Test {
    int f;
    public int j;
    int h;

    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        final Test test = (Test) o;
        return f == test.f && j == test.j && h == test.h;
    }

    public int hashCode() {
        return 0;
    }
}