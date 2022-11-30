class C {
    void foo(Object o) {
        switch (o) {
            case String s -> bar("A");
            case null -> bar("B");
            case Number n -> bar("A");
            default -> bar("C");
        }
    }
    void bar(String s){}
}