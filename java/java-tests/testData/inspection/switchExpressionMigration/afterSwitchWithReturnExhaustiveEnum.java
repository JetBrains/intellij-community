// "Replace with 'switch' expression" "true-preview"

class X {
    int test(E e) {
        return switch (e) {
            case A -> 3;
            case B -> 2;
        };
    }

    enum E {A, B}
}