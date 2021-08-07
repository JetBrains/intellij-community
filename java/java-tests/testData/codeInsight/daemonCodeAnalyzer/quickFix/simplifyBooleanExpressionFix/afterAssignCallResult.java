// "Simplify 'Objects.nonNull(...)' to true extracting side effects" "true"
import java.util.Set;
import java.util.Objects;

class X {
    void test(Set<String> set) {
        set.add("foo");
        boolean b = true;
    }
}