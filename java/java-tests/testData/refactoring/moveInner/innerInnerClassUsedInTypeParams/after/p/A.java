package p;

import java.util.List;

abstract class A implements List<A.B> {
    static class B {
    }
}