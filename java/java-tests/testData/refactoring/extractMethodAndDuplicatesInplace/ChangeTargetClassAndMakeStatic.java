public class Test {

    void test(int x) {
        new Runnable() {

            @Override
            public void run() {
                <selection>System.out.println(x);</selection>
            }
        };
    }
}