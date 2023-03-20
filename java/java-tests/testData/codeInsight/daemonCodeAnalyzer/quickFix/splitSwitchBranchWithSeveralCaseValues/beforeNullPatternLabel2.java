// "Split values of 'switch' branch" "true-preview"
class C {
    void foo(Object o) {
        String s = "";
        switch (o) {
            case null, Number n<caret>:
                s = "x";
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + o);
        }
    }
}