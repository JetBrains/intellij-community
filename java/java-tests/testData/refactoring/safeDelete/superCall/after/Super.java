class Super {
}

class Child extends Super {
    void foo() {
    }
}

class Usage {
    void bar(Child c) {
        c.foo();
    }
}