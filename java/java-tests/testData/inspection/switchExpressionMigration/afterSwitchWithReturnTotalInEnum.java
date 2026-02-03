// "Replace with 'switch' expression" "true-preview"

class X {
    int test(E e) {
        return switch (e) {
            case E ee -> 3;
        };
    }

    enum E {A, B}
}