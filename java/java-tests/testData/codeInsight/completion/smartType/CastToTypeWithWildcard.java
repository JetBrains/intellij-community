import java.util.List;

class Foo {
    {
        Object o;
        int size = o instanceof List<?> ? o.size<caret>
    }
}