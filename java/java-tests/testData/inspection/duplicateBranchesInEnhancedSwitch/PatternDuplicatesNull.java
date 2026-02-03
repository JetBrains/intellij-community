class C {
    void foo(Object o) {
        switch (o) {
            case null -> bar("A");
            case String s -> bar("B");
            case Number n -> bar("A");
            default -> bar("C");
        }
    }
    void bar(String s){}
}