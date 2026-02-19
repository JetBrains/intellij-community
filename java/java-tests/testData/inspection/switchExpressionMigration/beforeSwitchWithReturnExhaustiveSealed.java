// "Replace with 'switch' expression" "true-preview"

class X {
    int test(I i) {
        <caret>switch (i) {
            case C1 c1:
                return 3;
            case C2 c2:
                return 2;
        }
        return 5;
    }

    sealed interface I {}
    final class C1 implements I {}
    final class C2 implements I {}
}