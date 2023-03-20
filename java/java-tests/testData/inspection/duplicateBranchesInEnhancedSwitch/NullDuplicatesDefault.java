class C {
    void foo(String s) {
        switch (s) {
            case "blah blah blah", null -> bar("A");
            default -> bar("A");
        }
    }
    void bar(String s){}
}