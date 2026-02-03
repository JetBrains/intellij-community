abstract class A{
    abstract <S extends Number & Comparable<?>, T extends Number & Comparable<? extends S>> T foo(
            Comparable<? extends T> x,
            Comparable<? extends T> y);

    {
        foo(1, 1L);
    }
}