import java.io;

class A {
    public void test() {
        Inlined i = new Inlined();
    }
}

class <caret>Inlined {
    public static class A {
    }
}
