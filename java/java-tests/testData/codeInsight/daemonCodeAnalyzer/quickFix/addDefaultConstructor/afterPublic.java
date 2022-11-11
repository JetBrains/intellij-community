// "Add public no-args constructor to X" "true-preview"
public class X {
    X(int... a) {}
    X(String... b) {}

    public X() {
    }
}

class Y extends X {
}