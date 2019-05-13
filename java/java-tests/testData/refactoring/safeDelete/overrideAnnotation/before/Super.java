class Super {
    void fo<caret>o() {}
}

class Child extends Super {
    @Override
    void foo() {
    }
}

class Usage {
    void bar(Child c) {
        c.foo();
    }
}