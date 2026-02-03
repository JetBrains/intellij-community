import java.util.stream.Stream;

public class Foo {
    void m(Stream<String> s) {
        if (s.noneMatch(str -> str.equals("foo"))<caret>) {

        }
    }
}