class Tester {
    // IDEA-37432
    String callee(String x) {
        if (x == null) {
            return null;
        }
        return x;
    }

    void caller(String v) {
        String g = <caret>callee(v);
        Runnable r = () -> System.out.println(g);
    }
}
