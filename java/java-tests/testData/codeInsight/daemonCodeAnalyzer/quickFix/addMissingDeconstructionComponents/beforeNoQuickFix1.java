// "Add missing nested pattern" "false"
class Main {
    void foo(Rec<?, ?> obj) {
        if (obj instanceof Rec<?, ?>(int x<caret>)) {
        }
    }

    record Rec<T, U>(T x, U y) {}
}
