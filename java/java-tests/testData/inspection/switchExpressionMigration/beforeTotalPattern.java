// "Replace with 'switch' expression" "true"

class X {
    void test(Object o) {
        int a = 5;
        <caret>switch (o) {
            case Object oo:
                a = 3;
                break;
        }
    }
}