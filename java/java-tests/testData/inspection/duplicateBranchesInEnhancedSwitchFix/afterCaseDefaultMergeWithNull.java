// "Merge with 'case null'" "GENERIC_ERROR_OR_WARNING"
class C {
    void foo(Object o) {
        switch (o) {
            case null, default -> bar("A");
            case String s -> bar("B");
        }
    }
    void bar(String s){}
}