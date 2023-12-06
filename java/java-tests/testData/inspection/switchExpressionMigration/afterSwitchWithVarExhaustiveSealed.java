// "Replace with 'switch' expression" "true-preview"

class X {
    void test(I i) {
        int d = switch (i) {
            case C1 c1 -> 3;
            case C2 c2 -> 2;
        };
    }

    sealed interface I {}
    final class C1 implements I {}
    final class C2 implements I {}
}