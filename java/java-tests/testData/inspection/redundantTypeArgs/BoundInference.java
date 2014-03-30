import java.util.*;

class ArraysUtil {
    public static <S, T extends S> List<S> asList(T... a) {
        List<S> result = new ArrayList<S>();
        result.addAll(Arrays.asList(a));
        return result;
    }

    public static <S, T extends S> List<S> asCollection(T... a) {
        return ArraysUtil.<S, T>asList(a);
    }

    public static void main(String[] args) {
        asCollection();
    }
}

class ArraysUtil1 {
    public static <S, T> List<S> asList(T... a) {
        return null;
    }

    public static List<String> asCollection(Integer... a) {
        return ArraysUtil1.<warning descr="Explicit type arguments can be inferred"><String, Integer></warning>asList(a);
    }

    public static void main(String[] args) {
        asCollection();
    }
}