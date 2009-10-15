public class Aaaaaaa {

    void foo(Class<? extends Object> c) {}

    void bar() {
        foo((<caret>Class.forName("sdd"));
    }

}
