// "Remove redundant nested patterns" "true-preview"
class Main {
    void foo(Object obj) {
        if (obj instanceof Point(double w, double x)) {
        }
    }

    record Point(double x, double y) {}
}
