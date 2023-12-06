// "Replace with 'switch' expression" "true-preview"

class X {
    void test(E e) {
        int d = 5;
        <caret>switch (e) {
            case E ee:
                d = 3;
        }
    }

    enum E {A, B}
}