public class LocalClass {
    LocalClass field;

    public LocalClass(LocalClass o) {
    }

    void test() {
        new <caret>LocalClass(field) {
        };
    }
}
