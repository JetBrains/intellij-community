// "Merge with 'case 1'" "GENERIC_ERROR_OR_WARNING"
class C {
    void foo(int n) {
        String s = switch (n) {
            case 1, 3 -> throw new IllegalArgumentException("A");
            case 2 -> throw new IllegalStateException("A");
            default -> "";
        };
    }
}