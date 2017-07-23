public class Test {
    Long[] bar() {
        return new Long[0];
    }

    Long[][] foo(int n, int k) {
        Long[][] a = new Long[][]{new Long[0], new Long[0]};

        for (int i = 0; i < a.length; i++) {
            a[i] = bar();
        }

        return a;
    }
}
