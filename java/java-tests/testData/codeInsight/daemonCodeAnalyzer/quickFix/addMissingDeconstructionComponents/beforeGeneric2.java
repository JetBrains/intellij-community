// "Add missing nested patterns" "true-preview"
class Main {
    void foo(Rec<?, ?> obj) {
        if (obj instanceof Rec<?, ?>(<caret>)) {
        }
    }

    record Rec<T, U>(T x, U y) {}
}
