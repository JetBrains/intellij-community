import java.util.Arrays;

class Test {
    void foo(int[] a) {
        int log = 0;
        while (1 << log < <selection>a.length</selection>) {
            log++;
        }
        int n = 1 << log;
        int[] b = new int[2 * n];
        System.arraycopy(a, 0, b, n, a.length);
        Arrays.fill(b, n + a.length, 2 * n, -1);
    }
}