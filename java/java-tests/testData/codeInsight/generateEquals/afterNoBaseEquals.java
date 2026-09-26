public class Test {
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        return true;
    }

    public int hashCode() {
        return 0;
    }
}