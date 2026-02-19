// "Merge with 'case A'" "INFORMATION"
enum T {
    A, B, C;

    int foo(T t) {
        switch (t) {
            case A:
                return t.ordinal(); // comment 1

            case B:
                <caret>return t.ordinal();

            case C:
                return t.ordinal(); // comment 2

            default:
                return 0;
        }
    }
}