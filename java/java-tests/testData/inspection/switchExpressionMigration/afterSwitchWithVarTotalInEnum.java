// "Replace with 'switch' expression" "true"

class X {
    void test(E e) {
        int d = switch (e) {
            case E ee -> 3;
        };
    }

    enum E {A, B}
}