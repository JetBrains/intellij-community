import java.util.stream.Stream;

public class Foo {
    void m(Stream<String> s) {
        if (s.anyMatch(str -> str.equals("foo")).not<caret>) {

        }
    }
}