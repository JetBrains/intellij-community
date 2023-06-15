// "Replace with 'switch' expression" "true-preview"

class X {
    int test(I i) {
        return switch (i) {
            case Object ii -> 3;
        };
    }

    sealed interface I {}
    final class C1 implements I {}
    final class C2 implements I {}
}