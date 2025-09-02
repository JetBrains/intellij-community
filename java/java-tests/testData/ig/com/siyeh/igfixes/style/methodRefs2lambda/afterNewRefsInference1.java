// "Replace method reference with lambda" "true-preview"
public class MyTest {
    interface I {
       void m(Integer s);
    }
    static class Foo<X extends Number> {
        Foo(X x) { }
    }

 
    static void m(I s) {}

    static {
        m(x -> new Foo<Integer>(x));
    }
}
