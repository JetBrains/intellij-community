class C {
    String foo(int n) {
        String s;
        switch (n) {
            case 1 -> s = Integer.toString(n);
            case 2 -> <warning descr="Labeled rule's code block is redundant">{</warning> s = Integer.toString(n); <warning descr="Labeled rule's code block is redundant">}</warning>
            case 3 -> throw new RuntimeException();
            case 4 -> <warning descr="Labeled rule's code block is redundant">{</warning> throw new RuntimeException(); <warning descr="Labeled rule's code block is redundant">}</warning>
            case 5 -> <warning descr="Labeled rule's code block is redundant">{</warning> s = "a"; <warning descr="Labeled rule's code block is redundant">}</warning>
            default -> s = "b";
        };
        return s;
    }
}