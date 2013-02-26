import java.util.*;

class IDEA101169 {
    {
        List<List<UnaryOperator<String>>> intPermutationOfFunctions = perm(asList( s -> s.substring(0),  s -> s.substring(0),  s -> s.substring(0)));

        List<UnaryOperator<String>> p = asList( s -> s.substring(0),  s -> s.substring(0),  s -> s.substring(0));
    }

    public static <T> List<List<T>> perm(List<T> l) {
        return null;
    }

    @SafeVarargs
    public static <T> List<T> asList(T... a) {
        return null;
    }

    interface UnaryOperator<T> extends Function<T, T> {}

    interface Function<T, R> {
        public R apply(T t);
    }
}