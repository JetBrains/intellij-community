import java.util.ArrayList;
import java.util.Set;

public class Foo {
    void m(Set<String> o) {
        new ArrayList<>(o)<caret>
    }
}