// "Merge with 'case 1'" "INFORMATION"
class C {
    String foo(int n) {
        return switch (n) {
            case 1, 2 -> {
                foo(); // same comment
                break "A";
            }
            // another comment
            default -> "";
        };
    }
    void foo(){}
}