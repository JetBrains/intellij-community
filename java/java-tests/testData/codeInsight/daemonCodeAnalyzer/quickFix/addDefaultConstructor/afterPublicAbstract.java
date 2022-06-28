// "Add protected no-args constructor to X" "true"
public abstract class X {
    protected X(int... a) {}
    public X(String... b) {}

    protected X() {
    }
}

class Y extends X {
}