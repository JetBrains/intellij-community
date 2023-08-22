// "Replace with 'switch' expression" "true-preview"

class X {
    int test(E e) {
        <caret>switch (e) {
            case A:
                return 3;
            case B:
                return 1;
            default:
                return 2;
        }
    }

    enum E {A, B}
}