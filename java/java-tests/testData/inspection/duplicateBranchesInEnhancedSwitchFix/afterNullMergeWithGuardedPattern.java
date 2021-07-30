// "Merge with 'case Number n && n.intValue() == 42'" "GENERIC_ERROR_OR_WARNING"
class C {
    void foo(Object o) {
        switch (o) {
            case Number n && n.intValue() == 42, null -> bar("A");
            case String s -> bar("B");
            default -> bar("C");
        }
    }
    void bar(String s){}
}