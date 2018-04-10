
import java.util.*;

class Test {
    private final Collection<Integer> collection = new ArrayList<>();

    {
        List<Integer> l =  Collections.unmodifiableList((List<Integer>)this.collection);
        System.out.println(l);
    }
}