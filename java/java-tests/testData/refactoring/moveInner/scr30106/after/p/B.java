package p;

public class B extends X {
    private A outer;

    {
        method();
    }

    public B(A outer) {
        this.outer = outer;
    }
}
