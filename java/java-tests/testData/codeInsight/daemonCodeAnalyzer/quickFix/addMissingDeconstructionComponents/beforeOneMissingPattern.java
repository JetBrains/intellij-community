// "Add missing nested pattern" "true-preview"
class Main {
    void foo(Object obj) {
        switch (obj) {
            case Point(double x<caret>) -> {}
            default -> {}
        }
    }

    record Point(double x, double y) {}
}
