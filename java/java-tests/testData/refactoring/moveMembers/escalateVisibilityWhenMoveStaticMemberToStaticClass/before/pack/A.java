package pack;

import static pack.A.B.*;

public class A {
    void run() {
        z++;
        foo();
    }

    private static int z = 10;

    private static void foo() {}
    static class B {
    }
}