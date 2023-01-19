// "Add missing nested patterns" "true-preview"
class Main {
    void foo(Rec<String, Number> obj) {
        if (obj instanceof Rec<String, Number>(var x, var y)) {
        }
    }

    record Rec<T, U>(T x, U y) {}
}
