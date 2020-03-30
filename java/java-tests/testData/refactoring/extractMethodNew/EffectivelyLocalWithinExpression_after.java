public class EffectivelyLocalWithinExpression {
    void foo() {
        int n;
        if (newMethod(0)) {
            System.out.println();
        }
    }

    private boolean newMethod(int i) {
        int n;
        return (n = z()) > i && n < 100;
    }

    void bar() {
        int n;
        if (newMethod(1)) {
            System.out.println();
        }
    }

    int z() {
        return 1;
    }
}