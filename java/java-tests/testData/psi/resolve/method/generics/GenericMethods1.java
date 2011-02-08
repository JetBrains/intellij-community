import java.util.*;

public class AAA {
    <T> void f(java.util.List<T> t) {
    }

    void f(Collection<String> s) {
    }

    void y(List<String> strings) {
        <ref>f(strings);
    }
}
