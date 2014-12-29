public class Foo {
    void m() {
        doAct() + "aaa".try<caret>
    }

    String doAct() {
        return null;
    }
}