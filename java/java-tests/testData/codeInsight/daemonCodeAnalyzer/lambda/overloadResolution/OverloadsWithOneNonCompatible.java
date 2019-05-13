
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class Foo {

    public void foo(Set<String> types) { }

    public void foo(Collection<Integer> types) { }

    void m(final Stream<String> stringStream) {
        foo(stringStream.collect(Collectors.toSet()));
    }
}
