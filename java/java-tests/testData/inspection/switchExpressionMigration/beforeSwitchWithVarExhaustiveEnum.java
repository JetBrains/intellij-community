// "Replace with 'switch' expression" "true-preview"

class X {
    void test(E e) {
        int d = 5;
        <caret>switch (e) {
            case A:
                d = 3;
                break;
            case B:
                d = 2;
        }
    }

    enum E {A, B}
}