// "Create record 'Point'" "true-preview"
class Test {
    void foo(Object obj) {
        if (obj instanceof Point(double x, double y)) {}
    }
}

public record Point(double x, double y) {
}