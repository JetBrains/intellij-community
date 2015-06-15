import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class MyTest {
    void foo(Stream<List<Person>> listStream) {
        listStream.map(lp -> lp.stream().map(p -> p.name).collect(Collectors.joining("/", "<", ">")));
    }

    public static class Person {
        private String name = "";
    }
}
