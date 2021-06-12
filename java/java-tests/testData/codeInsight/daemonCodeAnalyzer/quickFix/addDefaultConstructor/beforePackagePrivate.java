// "Add package-private no-args constructor to X" "true"
class X {
    X(int... a) {}
    X(String... b) {}
}

class Y<caret> extends X {
}