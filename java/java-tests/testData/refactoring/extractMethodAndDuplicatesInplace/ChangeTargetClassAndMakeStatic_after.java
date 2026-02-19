public class Test {

    void test(int x) {
        new Runnable() {

            @Override
            public void run() {
                extracted(x);
            }
        };
    }

    private static void extracted(int x) {
        System.out.println(x);
    }
}