package p;

public class B extends X {
    private final A outer;

    {
        method();
    }

    public B(A outer) {
        this.outer = outer;
    }
}
