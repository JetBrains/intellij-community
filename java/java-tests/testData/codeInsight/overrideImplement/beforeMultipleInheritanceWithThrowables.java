interface A {
    void a() throws java.io.IOException;
}

interface B {
    void a() throws InstantiationException;
}

interface C extends A, B {}

class D implements C {
    <caret>
}