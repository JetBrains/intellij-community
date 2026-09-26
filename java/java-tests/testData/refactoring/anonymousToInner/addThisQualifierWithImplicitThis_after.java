public class LocalClass {
    LocalClass field;

    public LocalClass(LocalClass o) {
    }

    void test() {
        new MyClass();
    }

    private static class MyClass extends LocalClass {
        public MyClass() {
            super(LocalClass.this.field);
        }
    }
}
