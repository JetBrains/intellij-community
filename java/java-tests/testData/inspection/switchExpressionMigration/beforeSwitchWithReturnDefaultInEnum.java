// "Replace with 'switch' expression" "true"

class X {
    int test(E e) {
        <caret>switch (e) {
            case A:
                return 3;
            case B:
                return 1;
            case default:
                return 2;
        }
    }

    enum E {A, B}
}