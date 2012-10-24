abstract class C{
    abstract <T> T foo(T x, T y);

    {
        Long s = (Long) foo(1,1L);
    }
}