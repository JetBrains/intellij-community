import org.jetbrains.annotations.NotNull;

class C {
    void foo(String x, int a, int b) {
        String s1 = newMethod(sum(a, b), Math.max(a, b));
        String s2 = newMethod(sum(a, 0), Math.min(a, b) + 1);
        String s3 = newMethod(x, a - b);
    }

    @NotNull
    private String newMethod(String sum, int max) {
        return sum.substring(2, max);
    }

    String sum(int a, int b) {
        return a + " " + b;
    }
}