class Tmp
{
    interface Function<T, R> {

        R apply(T t);
    }
    interface Foo<T>
    {
        <R> Foo<R> map1(Function<T,R> f);

        <R> Foo<R> map2(Function<? super T, ? extends R> f);
    }

    public static void main(String[] args)
    {
        Foo<Object> x = null;
        Foo<Object> y1 = x.map1(i -> "");
        Foo<Object> y2 = x.map2(i -> "");
    }
}