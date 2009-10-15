public class Aaaaaaa {

    void foo(Class<? extends Object> c) {}

    void bar() {
        foo((Class<Object>) <caret>Class.forName("sdd"));
    }

}
