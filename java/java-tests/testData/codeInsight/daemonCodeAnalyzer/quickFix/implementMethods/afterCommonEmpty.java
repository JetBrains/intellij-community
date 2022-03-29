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

public class Impl implements I {
    @Override
    public <A> Optional<A> opt() {
        return Optional.empty();
    }

    @Override
    public <A> Stream<A> stream() {
        return Stream.empty();
    }

    @Override
    public <A> Collection<A> collection() {
        return Collections.emptyList();
    }

    @Override
    public <A> List<A> list() {
        return Collections.emptyList();
    }

    @Override
    public <A> Enumeration<A> enumeration() {
        return Collections.emptyEnumeration();
    }

    @Override
    public <A> Iterator<A> iterator() {
        return Collections.emptyIterator();
    }

    @Override
    public <A> ListIterator<A> listIterator() {
        return Collections.emptyListIterator();
    }

    @Override
    public <K, V> Map<K, V> map() {
        return Collections.emptyMap();
    }

    @Override
    public <A> Set<A> set() {
        return Collections.emptySet();
    }

    @Override
    public <K, V> NavigableMap<K, V> navMap() {
        return Collections.emptyNavigableMap();
    }

    @Override
    public <K, V> SortedMap<K, V> sortedMap() {
        return Collections.emptySortedMap();
    }

    @Override
    public <A> NavigableSet<A> navSet() {
        return Collections.emptyNavigableSet();
    }

    @Override
    public <A> SortedSet<A> sortedSet() {
        return Collections.emptySortedSet();
    }

    @Override
    public void method() {

    }

    @Override
    public String str() {
        return "";
    }

    @Override
    public int integer() {
        return 0;
    }
}