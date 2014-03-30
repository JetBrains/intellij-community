import java.util.List;

class B{
    public static void bar(){
       foo(null).get(0).compareTo(null);
    }
    static <T extends List<Comparable<T>>> T foo(T x) {
        return x;
    }
}
