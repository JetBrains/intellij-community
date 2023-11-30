class A {
    private A() {
    }

    static A <caret>createA() {
        return new A();
    }
}

public class B {
    A a = A.createA();
}