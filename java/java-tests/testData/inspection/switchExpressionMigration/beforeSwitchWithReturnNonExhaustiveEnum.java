// "Replace with 'switch' expression" "true"

class X {
    int test(E e) {
        <caret>switch (e) {
            case A:
                return 3;
        }
        return 5;
    }

    enum E {A, B}
}