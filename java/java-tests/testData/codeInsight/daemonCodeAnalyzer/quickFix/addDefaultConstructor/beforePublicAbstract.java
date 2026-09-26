// "Add protected no-args constructor to X" "true-preview"
public abstract class X {
    protected X(int... a) {}
    public X(String... b) {}
}

class Y<caret> extends X {
}