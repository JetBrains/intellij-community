import java.util.List;

class B{
    public static void bar(){
        <error descr="Inferred type 'java.util.List<java.lang.Comparable>' for type parameter 'T' is not within its bound; should implement 'java.util.List<java.lang.Comparable<java.util.List<java.lang.Comparable>>>'">foo(null)</error>.get(0).compareTo(null);
    }
    static <T extends List<Comparable<T>>> T foo(T x) {
        return x;
    }
}