public class C {
    void subject(String s, boolean b) {
    }

    void caller(boolean b) {
        int s;
        subject(null, b);
    }

    void caller1() {
        caller();
    }
}
