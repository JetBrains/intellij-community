import java.util.*;

class AAA {
    boolean addIntoTwoSets(Set<String> set1, Set<String> set2, String value) {
        if (set1.isEmpty())
            return set1.add(value) & set2.add(value);
        else
            return set1.add(value) | set2.add(value);
    }

    void foo() {
        Set<String> s1 = new HashSet<>();
        Set<String> s2 = new HashSet<>();
        <caret>addIntoTwoSets(s1, s2, "foo");
    }
}