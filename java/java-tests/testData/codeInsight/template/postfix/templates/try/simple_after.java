public class Foo {
    void m() {
        try {
            doAct()
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    void doAct() {}
}