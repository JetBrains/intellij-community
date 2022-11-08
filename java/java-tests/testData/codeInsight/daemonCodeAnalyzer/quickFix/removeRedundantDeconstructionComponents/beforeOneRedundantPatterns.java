// "Remove redundant nested pattern" "true-preview"
class Main {
    void foo(Object obj) {
        if (obj instanceof Point(double x, double y, double z<caret>)) {
        }
    }

    record Point(double x, double y) {}
}
