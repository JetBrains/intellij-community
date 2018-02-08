package p;

class B extends S{
    private A a;

    public B(A a) {
    super(a.foo());
        this.a = a;
    }
}
