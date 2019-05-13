public class Foo {
    void m() {
        try {
            doAct() + "aaa"
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    String doAct() {
        return null;
    }
}