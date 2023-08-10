// "Add missing nested patterns" "true-preview"
class Main {
    void foo(Object obj) {
        switch (obj) {
            case Point(<caret>) -> {}
            default -> {}
        }
    }

    record Point(double x, double y) {}
}
