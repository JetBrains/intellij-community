public class Foo {
    void m() {
        try {
            doAct() + "aaa"
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    String doAct() {
        return null;
    }
}