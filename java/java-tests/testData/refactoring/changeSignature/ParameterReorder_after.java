class A {
    public int method<caret>(int j, int i) {
        return i - j;
    }
}

class B extends A {
    public void method(int i, int j) {
        return i - j;
    }
}