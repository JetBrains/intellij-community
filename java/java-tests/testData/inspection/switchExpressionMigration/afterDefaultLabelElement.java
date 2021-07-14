// "Replace with 'switch' expression" "true"

class X {
    void test(Integer i) {
        int a = switch (o) {
            case 1, default -> 3;
        };
    }
}