package p;

class B extends S{
    private final A a;

    public B(A a) {
    super(a.foo());
        this.a = a;
    }
}
