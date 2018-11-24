public class Test {
    int sum(Integer i, int j) {
        return i + j;
    }

    int[] foo(int n, int k) {
        int[] a = new int[] {1, 2, 3, 4};

        for (int i = 0; i < a.length; i++) {
            a[i] = sum(i, k);
        }

        return a;
    }
}
