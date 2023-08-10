class A {
    private A() {
    }

    static A createA<caret>() {
        return new A();
    }
}

public class B {
    A a = A.createA();
}