import java.util.*;
interface Stream<T> {
    <R> Stream<R> map(Function<? super T, ? extends R> mapper);
}

interface Function<T, R> {
    R apply(T t);
}

class Test1
{
    public static void main(Stream<Map.Entry<String, Long>> stream)
    {
        Stream<String> map = stream.map(Map.Entry::getKey);
    }

}
