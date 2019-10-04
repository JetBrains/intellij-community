class C {
    String foo(int n) {
        return switch (n) {
            <warning descr="Labeled rule's result expression can be wrapped with code block">case 1 -> Integer.toString(n);</warning>
            case 2 -> { yield Integer.toString(n); }
            <warning descr="Labeled rule's statement can be wrapped with code block">case 3 -> throw new RuntimeException();</warning>
            case 4 -> { throw new RuntimeException(); }
            case 5 -> { yield "a";}
            <warning descr="Labeled rule's result expression can be wrapped with code block">default -> "b";</warning>
        };
    }
}