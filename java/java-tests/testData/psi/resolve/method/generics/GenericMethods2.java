import java.util.*;

public class AAA {
    <T> void f(Collection<T> t) {
    }

    void f(java.util.List<String> s) {
    }

    void y(List<String> strings) {
        <ref>f(strings);
    }
}
