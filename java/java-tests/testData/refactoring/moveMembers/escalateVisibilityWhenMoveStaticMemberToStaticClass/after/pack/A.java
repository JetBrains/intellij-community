package pack;

import static pack.A.B.*;

public class A {
    void run() {
        z++;
        foo();
    }

    static class B {
        static int z = 10;

        static void foo() {}
    }
}