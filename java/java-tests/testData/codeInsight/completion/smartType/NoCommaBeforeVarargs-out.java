class Foooo {
    int bar(int a, Object... varargs) {}

    int foo() {
        bar(hashCode()<caret>);
    }

}
