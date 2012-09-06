class A {

    void n(B b) {
        m(b);
    }

    void m<caret>(B b) {
        System.out.print("display for me the shape" + this + " " + b.getI() + "times");
    }
}

class B {
    private final int i;

    public B(int i) {
        this.i = i;
    }


    public int getI() {
        return i;
    }
}