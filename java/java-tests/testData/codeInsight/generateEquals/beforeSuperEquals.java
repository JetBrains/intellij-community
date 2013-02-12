abstract class SuperTest {
    public boolean equals(Object object) {
        return true;
    }
    public int hashCode() {
        return 0;
    }
}
class Test extends SuperTest {<caret>
}