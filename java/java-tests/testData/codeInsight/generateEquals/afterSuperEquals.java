abstract class SuperTest {
    public boolean equals(Object object) {
        return true;
    }
    public int hashCode() {
        return 0;
    }
}
class Test extends SuperTest {
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        return true;
    }
}