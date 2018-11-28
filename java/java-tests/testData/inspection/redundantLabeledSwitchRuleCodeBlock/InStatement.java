class C {
    String foo(int n) {
        String s;
        switch (n) {
            case 1 -> s = Integer.toString(n);
            <warning descr="Labeled rule's code block is redundant">case</warning> 2 -> { s = Integer.toString(n); }
            case 3 -> throw new RuntimeException();
            <warning descr="Labeled rule's code block is redundant">case</warning> 4 -> { throw new RuntimeException(); }
            <warning descr="Labeled rule's code block is redundant">case</warning> 5 -> { s = "a"; }
            default -> s = "b";
        };
        return s;
    }
}