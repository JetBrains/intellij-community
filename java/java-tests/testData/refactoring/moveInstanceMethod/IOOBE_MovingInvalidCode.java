class A {
}

class B {
}

public class Main {
    void user() {
        method(, new B());
    }

    void <caret>method(A a, B b) {
    }
}