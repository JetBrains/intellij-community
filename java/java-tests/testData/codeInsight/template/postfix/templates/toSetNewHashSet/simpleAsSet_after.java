import java.util.HashSet;
import java.util.Set;

public class Foo {
    void m(Set<String> o) {
        new HashSet<>(o)<caret>
    }
}