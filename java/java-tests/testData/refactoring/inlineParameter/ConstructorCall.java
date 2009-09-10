class A {
    private int myI;

    private A(int <caret>i) {
        myI = i;
    }

    public static A create() {
        return new A(0);
    }
}