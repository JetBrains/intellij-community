class C {
    String foo(int n) {
        return switch (n) {
            <warning descr="Labeled rule's result can be wrapped with code block">case</warning> 1 -> Integer.toString(n);
            case 2 -> { break Integer.toString(n); }
            <warning descr="Labeled rule's statement can be wrapped with code block">case</warning> 3 -> throw new RuntimeException();
            case 4 -> { throw new RuntimeException(); }
            case 5 -> { break "a";}
            <warning descr="Labeled rule's result can be wrapped with code block">default</warning> -> "b";
        };
    }
}