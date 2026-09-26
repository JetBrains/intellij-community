// "Create constructor matching super" "true"
class X {
  X(int... a) {}
  X(String... b) {}
}

class Y extends X {
    Y(int... a) {
        super(a);
    }

    Y(String... b) {
        super(b);
    }
}