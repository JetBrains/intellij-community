// "Remove redundant nested patterns" "true-preview"
class Main {
    void foo(Object obj) {
        if (obj instanceof Point(double w, double x, double y, double z<caret>)) {
        }
    }

    record Point(double x, double y) {}
}
