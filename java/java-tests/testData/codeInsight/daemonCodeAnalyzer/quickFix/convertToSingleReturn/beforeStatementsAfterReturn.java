// "Transform body to single exit-point form" "true"
class Test {
    String <caret>test(String s) {
        return "foo";
        return "bar";
    }
}