// "Replace with 'switch' expression" "true-preview"

class X {
    int test(I i) {
        <caret>switch (i) {
            case Object ii:
                return 3;
        }
    }

    sealed interface I {}
    final class C1 implements I {}
    final class C2 implements I {}
}