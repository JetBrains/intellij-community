package p;

import java.util.List;

public class Main {
    static abstract class A implements List<A.B> {
        static class B {
        }
    }
}
