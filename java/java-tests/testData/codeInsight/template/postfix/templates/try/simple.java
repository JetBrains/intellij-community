public class Foo {
    void m() {
        doAct().try<caret>
    }

    void doAct() {}
}