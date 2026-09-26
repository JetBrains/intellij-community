class Tester {
    // IDEA-37432
    String callee(String x) {
        if (x == null) {
            return null;
        }
        return x;
    }

    void caller(String v) {
        final String g = <caret>callee(v);
        System.out.println(g);
    }
}
