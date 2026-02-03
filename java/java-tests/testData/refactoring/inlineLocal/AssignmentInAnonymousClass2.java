class X {
   void use() {
        int x = 2;
        new A(x, x = 3, <caret>x) {};
    }

    static class A {
        A(int x1, int x2, int x3) {}
    }
}