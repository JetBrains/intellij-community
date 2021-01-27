// "Fix all 'Redundant 'Collection.addAll()' call' problems in file" "true"
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.PriorityBlockingQueue;

public class InheritComparator {
    public static void main(String[] args) {
        SortedSet<String> c1 = new TreeSet<>(Comparator.reverseOrder());
        Collection<String> c2 = new TreeSet<>(Comparator.reverseOrder());
        Collection<String> c3 = new HashSet<>();
        Collection<String> c4 = new PriorityQueue<>();
        Collection<String> c5 = new PriorityBlockingQueue<>();
    
        Collection<String> hs1 = new HashSet<>();
        hs1.<caret>addAll(c1);
        Collection<String> hs2 = new HashSet<>();
        hs2.addAll(c2);
        Collection<String> hs3 = new HashSet<>();
        hs3.addAll(c3);
        Collection<String> hs4 = new HashSet<>();
        hs4.addAll(c4);
        Collection<String> hs5 = new HashSet<>();
        hs5.addAll(c5);
    
        Collection<String> ts1 = new TreeSet<>();
        ts1.addAll(c1);
        Collection<String> ts2 = new TreeSet<>();
        ts2.addAll(c2);
        Collection<String> ts3 = new TreeSet<>();
        ts3.addAll(c3);
        Collection<String> ts4 = new TreeSet<>();
        ts4.addAll(c4);
        Collection<String> ts5 = new TreeSet<>();
        ts5.addAll(c5);
    
        Collection<String> pq1 = new PriorityQueue<>();
        pq1.addAll(c1);
        Collection<String> pq2 = new PriorityQueue<>();
        pq2.addAll(c2);
        Collection<String> pq3 = new PriorityQueue<>();
        pq3.addAll(c3);
        Collection<String> pq4 = new PriorityQueue<>();
        pq4.addAll(c4);
        Collection<String> pq5 = new PriorityQueue<>();
        pq5.addAll(c5);
    
        Collection<String> pbq1 = new PriorityBlockingQueue<>();
        pbq1.addAll(c1);
        Collection<String> pbq2 = new PriorityBlockingQueue<>();
        pbq2.addAll(c2);
        Collection<String> pbq3 = new PriorityBlockingQueue<>();
        pbq3.addAll(c3);
        Collection<String> pbq4 = new PriorityBlockingQueue<>();
        pbq4.addAll(c4);
        Collection<String> pbq5 = new PriorityBlockingQueue<>();
        pbq5.addAll(c5);
    
        SortedMap<String, String> m1 = new TreeMap<>(Comparator.reverseOrder());
        Map<String, String> m2 = new TreeMap<>(Comparator.reverseOrder());
    
        Map<String, String> tm1 = new TreeMap<>();
        tm1.putAll(m1);
        Map<String, String> tm2 = new TreeMap<>();
        tm2.putAll(m2);
    
        Map<String, String> cslm1 = new ConcurrentSkipListMap<>();
        cslm1.putAll(m1);
        Map<String, String> cslm2 = new ConcurrentSkipListMap<>();
        cslm2.putAll(m2);
    }
}
