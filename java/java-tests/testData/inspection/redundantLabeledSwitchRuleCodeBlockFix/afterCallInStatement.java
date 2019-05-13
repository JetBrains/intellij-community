// "Unwrap code block of labeled rule" "LIKE_UNUSED_SYMBOL"
class C {
    void foo(int n) {
        String s;
        switch (n) {
            case 1 -> System.out.println(n);
            default -> System.out.println();
        };
    }
}