public class Test {
    int sum(int i, int j) {
        return i + j;
    }

    int[] foo(int n, int k) {
        int[] a = new int[n];

        for (int i = 0; i < a.length; i++) {
            a[i] = sum(i, k);
        }

        return a;
    }
}
