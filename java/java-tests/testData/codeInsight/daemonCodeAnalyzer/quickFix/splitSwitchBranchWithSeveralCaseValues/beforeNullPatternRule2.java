// "Split values of 'switch' branch" "true"
class C {
    void foo(Object o) {
        String s = "";
        switch (o) {
            case null, Number n<caret> -> s = "x";
            default -> throw new IllegalStateException("Unexpected value: " + o);
        }
    }
}