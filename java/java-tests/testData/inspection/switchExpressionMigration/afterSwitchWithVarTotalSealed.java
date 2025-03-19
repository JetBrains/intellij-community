// "Replace with 'switch' expression" "true-preview"

class X {
    void test(I i) {
        int d = switch (i) {
            case Object ii -> 3;
        };
    }

    sealed interface I {}
    final class C1 implements I {}
    final class C2 implements I {}
}