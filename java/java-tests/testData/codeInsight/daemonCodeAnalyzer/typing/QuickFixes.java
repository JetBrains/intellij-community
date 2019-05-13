import java.io.*;

class Test {
    float y;
    void f() {
        int x = 11;
        System.out.println("aap");
        if (Math.random() > 0.5) {
            for (int j = 0; j < 10; j++) {
                System.out.println(x);
            }
        }
        try {
            g(0);
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }

        try {
            g(0);
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }
    void foo() {
        try {
            g(0);
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    /**
     *
     * @param u
     */
    private void g(int u) {
        int x = 12;
        System.out.println("foo");
        System.out.println("bar");
        System.out.println(x);
    }
}
