import java.util.List;

class Foo {
    {
        Object o;
        int size = o instanceof List<?> ? ((List<?>) o).size() : <caret>
    }
}