// "Replace with 'switch' expression" "true"

class X {
    void test(Integer i) {
        int a = 5;
        <caret>switch (o) {
            case 1, default:
                a = 3;
                break;
        }
    }
}