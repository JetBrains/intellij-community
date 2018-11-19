import java.util.function.Function;

class TestClass {

  <H> void foo(Function<A, H> f, Function<H, String> toStr) {}

    {
       foo(o1 -> A.getB(o1), o2 -> o2.getId());
    }
}

class A {
    static B getB(A a) { return null; }
    static B getB(String a) { return null; }
}

interface B {
    String getId();
}