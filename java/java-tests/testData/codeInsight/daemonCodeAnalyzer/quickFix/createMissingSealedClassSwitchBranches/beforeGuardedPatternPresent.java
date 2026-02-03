// "Create missing branch 'Sub1'" "true-preview"
sealed interface I {}
final class Sub1 implements I {}
final class Sub2 implements I {}

class Test {
    void test(I i) {
        switch (i<caret>) {
            case Sub1 sub1 when Math.random() > 0.5:
                break;
            case Sub2 sub2:
                break;
        }
    }
}