// "Split values of 'switch' branch" "true"
class C {
    void foo(Object o) {
        String s = "";
        switch (o) {
            case null<caret>, Number n -> s = "x";
            default -> throw new IllegalStateException("Unexpected value: " + o);
        }
    }
}