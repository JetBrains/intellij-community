// "Create inner record 'Point'" "true-preview"
class Test {
    void foo(Object obj) {
        switch (obj) {
            case Point(double x, double y) -> {}
            default -> {}
        }
    }

    private record Point(double x, double y) {
    }
}