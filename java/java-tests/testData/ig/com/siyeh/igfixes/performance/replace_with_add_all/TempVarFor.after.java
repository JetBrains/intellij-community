import java.util.*;

class T {
    void f(Set<String> t, String[] f) {
        t.addAll(Arrays.asList(f).subList(2, f.length));
    }
}