// "Add public no-args constructor to X" "true-preview"
public class X {
    X(int... a) {}
    X(String... b) {}
}

class Y<caret> extends X {
}