// "Merge with 'case A'" "GENERIC_ERROR_OR_WARNING"
enum T {
    A, B, C;

    int foo(T t) {
        switch (t) {
            case A, B -> {
                return t.ordinal(); // comment 1
                }
            case C -> {
                return t.ordinal(); // comment 2
            }
            default -> {
                return 0;
            }
        }
    }
}