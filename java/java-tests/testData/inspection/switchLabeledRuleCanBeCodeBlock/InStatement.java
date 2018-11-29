class C {
    String foo(int n) {
        String s;
        switch (n) {
            <warning descr="Labeled rule's statement can be wrapped with code block">case</warning> 1 -> s = Integer.toString(n);
            case 2 -> { s = Integer.toString(n); }
            <warning descr="Labeled rule's statement can be wrapped with code block">case</warning> 3 -> throw new RuntimeException();
            case 4 -> { throw new RuntimeException(); }
            case 5 -> { s = "a"; }
            <warning descr="Labeled rule's statement can be wrapped with code block">default</warning> -> s = "b";
        };
        return s;
    }
}