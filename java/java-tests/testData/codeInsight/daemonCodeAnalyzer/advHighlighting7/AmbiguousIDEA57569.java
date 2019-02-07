package pck;
abstract class C<T>  {
    abstract Object foo(T x);
    String foo(String x) { return null; }
}

class D extends C<String>{
    {
        foo("");
    }
}
