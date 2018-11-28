class C {
    String foo(int n) {
        return switch (n) {
            case 1 -> Integer.toString(n);
            <warning descr="Labeled rule's code block is redundant">case</warning> 2 -> { break Integer.toString(n); }
            case 3 -> throw new RuntimeException();
            <warning descr="Labeled rule's code block is redundant">case</warning> 4 -> { throw new RuntimeException(); }
            <warning descr="Labeled rule's code block is redundant">case</warning> 5 -> { break "a";}
            default -> "b";
        };
    }
}