public class C {
    void subject(String s, boolean b) {
    }

    void caller(boolean b) {
        int s;
        subject(null, b);
    }

    void caller1() {
        caller(true);
    }
}

class B extends C {
    void caller(boolean b) {
        subject(null, b);
    }
}