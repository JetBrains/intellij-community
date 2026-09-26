// "Replace with 'switch' expression" "true-preview"

class X {
    void test(E e) {
        int d = switch (e) {
            case A -> 3;
            default -> 5;
        };
    }

    enum E {A, B}
}