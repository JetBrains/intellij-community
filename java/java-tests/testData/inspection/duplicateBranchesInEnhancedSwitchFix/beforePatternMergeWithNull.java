// "Merge with 'case null'" "GENERIC_ERROR_OR_WARNING"
class C {
    void foo(Object o) {
        switch (o) {
            case null -> bar("A");
            case String s -> bar("B");
            case Number n -> <caret>bar("A");
            default -> bar("C");
        }
    }
    void bar(String s){}
}