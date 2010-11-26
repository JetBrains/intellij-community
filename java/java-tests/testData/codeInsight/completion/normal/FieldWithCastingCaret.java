public class TestClass {
    int abc;

    public TestClass create(Object o) {
        if (o instanceof TestClass) {
            o.ab<caret>
        }
    }
}
