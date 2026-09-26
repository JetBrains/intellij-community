// "Split values of 'switch' branch" "true-preview"
class C {
    void foo(Object o) {
        String s = "";
        switch (o) {
            case Number n, null<caret> -> s = "x";
            default -> throw new IllegalStateException("Unexpected value: " + o);
        }
    }
}