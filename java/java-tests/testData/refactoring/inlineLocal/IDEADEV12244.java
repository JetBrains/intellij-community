import java.util.*;

class IDEADEV12244 {
    {
        TreeMap<String, Set<Integer>> map = new TreeMap<String, Set<Integer>>();
        Set<Integer> set = Collections.emptySet();
        map.put("foo", <caret>set);
    }
}