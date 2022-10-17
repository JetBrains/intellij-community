// "Create record 'Point'" "true-preview"
class Test {
    void foo(Object obj) {
        switch (obj) {
            case Poi<caret>nt(double x, double y) -> {}
            default -> {}
        }
    }
}