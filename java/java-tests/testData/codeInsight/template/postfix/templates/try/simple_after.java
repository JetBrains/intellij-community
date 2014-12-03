public class Foo {
    void m() {
        try {
            doAct()
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void doAct() {}
}