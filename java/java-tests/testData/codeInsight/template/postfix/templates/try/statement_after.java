public class Foo {
    void m() {
        try {
            Object o = new Object()<caret>
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}