import java.util.*;
class Test {
    Map<String, List<String>> f;
    void foo(String s) {
        List<String> stringList = f.get(s);
        stringList.add(s);
    }
}
