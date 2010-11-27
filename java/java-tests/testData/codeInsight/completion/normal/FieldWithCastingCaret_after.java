public class TestClass {
    int abc;

    public TestClass create(Object o) {
        if (o instanceof TestClass) {
            ((TestClass) o).abc<caret>
        }
    }
}
