// "Cast expression to 'B'" "true-preview"
import java.util.*;

class A { }

class B extends A {
    public B getStrings() {
        return (B) getFirstItem(new ArrayList<A>());
    }

    public static <T> T getFirstItem(Set<T> items) { return null; }
    public static <T> T getFirstItem(List<T> items) { return null; }
}