class C {
    void foo(Object o) {
        switch (o) {
            case Number n -> bar("A");
            case String s -> bar("B");
            case null -> bar("A");
            default -> bar("C");
        }
    }
    void bar(String s){}
}