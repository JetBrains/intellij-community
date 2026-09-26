public class EffectivelyLocalWithinExpression {
    void foo() {
        int n;
        if (<selection>(n = z()) > 0 && n < 100</selection>) {
            System.out.println();
        }
    }

    void bar() {
        int n;
        if ((n = z()) > 1 && n < 100) {
            System.out.println();
        }
    }

    int z() {
        return 1;
    }
}