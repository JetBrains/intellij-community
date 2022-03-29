// "Implement methods" "true"
import java.util.*;
import java.util.stream.*;

public interface I {
    <A> Optional<A> opt();
    <A> Stream<A> stream();
    <A> Collection<A> collection();
    <A> List<A> list();
    <A> Enumeration<A> enumeration();
    <A> Iterator<A> iterator();
    <A> ListIterator<A> listIterator();
    <K, V> Map<K, V> map();
    <A> Set<A> set();
    <K, V> NavigableMap<K, V> navMap();
    <K, V> SortedMap<K, V> sortedMap();
    <A> NavigableSet<A> navSet();
    <A> SortedSet<A> sortedSet();
    void method();
    String str();
    int integer();
}

public class I<caret>mpl implements I {
}