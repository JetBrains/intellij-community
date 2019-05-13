class Foo1 {
    void foo() {
        String <caret>s = <flown1>bar();
    }

    String bar() {
        String res;
        res = <flown111>"a";
        return <flown11>res;
    }
}
