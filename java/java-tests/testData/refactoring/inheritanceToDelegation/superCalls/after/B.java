class B {
    public final A myDelegate;

    public B() {
        myDelegate = new A(1);
    }
    
    public B(int i) {
        myDelegate = new A(i);
    }
}