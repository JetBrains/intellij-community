// "Merge with 'case Number n'" "GENERIC_ERROR_OR_WARNING"
class C {
    void foo(Object o) {
        switch (o) {
            case Number n -> bar("A");
            case String s -> bar("B");
            case null -> <caret>bar("A");
            default -> bar("C");
        }
    }
    void bar(String s){}
}