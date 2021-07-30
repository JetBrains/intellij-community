// "Merge with 'case default'" "GENERIC_ERROR_OR_WARNING"
class C {
    void foo(Object o) {
        switch (o) {
            case default -> bar("A");
            case String s -> bar("B");
            case null -> <caret>bar("A");
        }
    }
    void bar(String s){}
}