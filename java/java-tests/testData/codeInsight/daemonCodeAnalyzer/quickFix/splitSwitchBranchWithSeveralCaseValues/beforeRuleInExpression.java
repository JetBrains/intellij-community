// "Split values of 'switch' branch" "true"
class C {
    void foo(int n) {
        String s = switch (n) {
            <caret>case 1, 2 -> "x";
            default -> "";
        };
    }
}