public class Aaaaaaa {

    void foo(Class<? extends Aaaaaaa> c) {}

    void bar() {
        foo((<caret>Class.forName("sdd"));
    }

}
