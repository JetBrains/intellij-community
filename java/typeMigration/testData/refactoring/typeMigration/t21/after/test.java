import java.util.*;
class Test {
    Map<String, Set<String>> f;
    void foo(String s) {
        Set<String> stringList = f.get(s);
        stringList.add(s);
    }
}
