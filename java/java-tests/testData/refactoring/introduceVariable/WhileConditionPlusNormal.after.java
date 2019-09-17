import java.util.Arrays;

class Test {
    void foo(int[] a) {
        int log = 0;
        int temp = a.length;
        while (1 << log < temp) {
            log++;
        }
        int n = 1 << log;
        int[] b = new int[2 * n];
        System.arraycopy(a, 0, b, n, temp);
        Arrays.fill(b, n + temp, 2 * n, -1);
    }
}