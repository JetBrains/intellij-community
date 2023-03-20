// "Add missing nested pattern" "false"
class Main {
    void foo(Object obj) {
        if (obj instanceof Point(int x<caret>)) {
        }
    }

    record Point(double x, double y) {}
}
