// "Transform body to single exit-point form" "true"
class Test {
    String <caret>test(int x) {
        switch (x) {
            case 1:return "foo";
            case 2:return "bar";
            case 3:return "baz";
            default:return "qux";
        }
    }
}