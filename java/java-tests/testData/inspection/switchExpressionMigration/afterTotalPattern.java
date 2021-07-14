// "Replace with 'switch' expression" "true"

class X {
    void test(Object o) {
        int a = switch (o) {
            case Object oo -> 3;
        };
    }
}