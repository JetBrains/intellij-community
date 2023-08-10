// "Replace with 'switch' expression" "true-preview"

class X {
    int test(E e) {
        <caret>switch (e) {
            case E ee:
                return 3;
        }
    }

    enum E {A, B}
}