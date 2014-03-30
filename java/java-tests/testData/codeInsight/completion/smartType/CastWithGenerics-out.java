public class Aaaaaaa {

    void foo(Class<? extends Aaaaaaa> c) {}

    void bar() {
        foo((Class<? extends Aaaaaaa>) <caret>Class.forName("sdd"));
    }

}
