class C {
    void foo(String x, int a, int b) {
        String s1 = <selection>sum(a, b).substring(2, Math.max(a, b))</selection>;
        String s2 = sum(a, 0).substring(2, Math.min(a, b) + 1);
        String s3 = x.substring(2, a - b);
    }

    String sum(int a, int b) {
        return a + " " + b;
    }
}