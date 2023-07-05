class A {
    private A() {
    }

    static A <selection><caret>createA</selection>() {
        return new A();
    }
}

public class B {
    A a = A.createA();
}