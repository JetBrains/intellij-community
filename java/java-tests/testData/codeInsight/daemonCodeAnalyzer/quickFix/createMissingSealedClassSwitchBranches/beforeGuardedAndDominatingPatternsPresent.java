// "Create missing switch branch 'Sub1'" "true-preview"
sealed interface I {}
sealed interface J extends I {}
final class Sub1 implements I {}
final class Sub2 implements I {}
final class Sub3 implements I, J {}

class Test {
    void test(I i) {
        switch (i<caret>) {
            case Sub1 sub1 && Math.random() > 0.5:
                break;
            case Sub2 sub2:
                break;
            case J j:
                break;
        }
    }
}