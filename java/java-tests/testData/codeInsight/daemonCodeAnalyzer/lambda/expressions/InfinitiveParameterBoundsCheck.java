import java.util.*;

abstract class Test {
    {
        hasEntry(equalTo("parentId"), equalTo(1));
    }

    public abstract <T> List<T> equalTo(T operand);
    public abstract <K, V> void hasEntry(List<? super K> keyMatcher, List<? super V> valueMatcher);
}