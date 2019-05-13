interface Nat {
    interface Nil extends Nat {}
}

interface Inc extends Nat {
    Nat dec();

    static Inc make(Nat dec) {
        return () -> dec;
    }
}