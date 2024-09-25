abstract class SuperTest {
    public abstract boolean equals(Object object);
}
class Test extends SuperTest {
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        return true;
    }

    public int hashCode() {
        return 0;
    }
}