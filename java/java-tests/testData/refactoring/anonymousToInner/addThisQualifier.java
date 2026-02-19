public class LocalClass {
    public LocalClass(LocalClass o) {
    }

    void test() {
        new <caret>LocalClass(this) {
        };
    }
}
