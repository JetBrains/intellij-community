// "Replace with 'switch' expression" "true"

class X {
    void test(E e) {
        int d = 5;
        <caret>switch (e) {
            case A:
                d = 3;
        }
    }

    enum E {A, B}
}