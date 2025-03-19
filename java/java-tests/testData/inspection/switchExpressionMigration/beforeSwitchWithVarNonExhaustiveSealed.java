// "Replace with 'switch' expression" "true-preview"

class X {
    void test(I i) {
        int d = 5;
        <caret>switch (i) {
            case C1 c1:
                d = 3;
        }
    }

    sealed interface I {}
    final class C1 implements I {}
    final class C2 implements I {}
}