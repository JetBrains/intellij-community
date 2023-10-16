// "Replace with 'switch' expression" "true-preview"

class X {
    void test(Integer i) {
        int a = switch (o) {
            default -> 3;
        };
    }
}