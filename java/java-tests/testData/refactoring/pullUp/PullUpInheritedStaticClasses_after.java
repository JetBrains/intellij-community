public class A extends AA {

    static class B {}
}

class AA {
    static class C extends D {}

    static class D extends A.B {}
}