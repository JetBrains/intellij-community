abstract class C {
    void method() {
        method(27);
    }

    abstract void <caret>method(int i);
}

class C1 extends C {
    void method(int i) {
    }
}