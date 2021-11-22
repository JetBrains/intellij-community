// "Merge with 'case default'" "GENERIC_ERROR_OR_WARNING"
class C {
    void foo(Object o) {
        switch (o) {
            case default, null -> bar("A");
            case String s -> bar("B");
        }
    }
    void bar(String s){}
}