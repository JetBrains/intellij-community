package p;

public class B extends X {
    {
        method();
    }

    private A outer;

    public B(A outer) {
        this.outer = outer;
    }
}