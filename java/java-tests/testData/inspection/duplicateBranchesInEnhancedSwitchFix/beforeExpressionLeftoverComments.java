// "Merge with 'case 1'" "GENERIC_ERROR_OR_WARNING"
class C {
    String foo(int n) {
        return switch (n) {
            case 1 -> {
                foo(); // same comment
                break "A";
            }
            case 2 ->{
                foo(); // same comment
                <caret>break "A"; // another comment
            }
            default -> "";
        };
    }
    void foo(){}
}