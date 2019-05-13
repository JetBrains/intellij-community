public class Foo {
    void m() {
        doAct().try<caret>
        int i=0;

    }

    void doAct() {}
}