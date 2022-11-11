// "Transform body to single exit-point form" "true-preview"
class Test {
    String <caret>test(int x) {
        synchronized(this) {
            if(x == 0) return "foo";
            if(x == 1) return "bar";
        }
        return "baz";
    }
}