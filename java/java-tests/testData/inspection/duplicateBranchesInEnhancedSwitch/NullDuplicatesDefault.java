class C {
    void foo(String s) {
        switch (s) {
            case "blah blah blah" -> <weak_warning descr="Branch in 'switch' is a duplicate of the default branch">bar("A");</weak_warning>
            case null -> <weak_warning descr="Branch in 'switch' is a duplicate of the default branch">bar("A");</weak_warning>
            default -> bar("A");
        }
    }
    void bar(String s){}
}