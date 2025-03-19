// "Replace with 'switch' expression" "true-preview"

class X {
    void test(I i) {
        int d = 5;
        <caret>switch (i) {
            case C1 c1:
                d = 3;
                break;
            case C2 c2:
                d = 2;
        }
    }

    sealed interface I {}
    final class C1 implements I {}
    final class C2 implements I {}
}