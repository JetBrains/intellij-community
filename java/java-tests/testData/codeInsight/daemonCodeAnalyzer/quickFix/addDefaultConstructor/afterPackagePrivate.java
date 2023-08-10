// "Add package-private no-args constructor to X" "true-preview"
class X {
    X(int... a) {}
    X(String... b) {}

    X() {
    }
}

class Y extends X {
}