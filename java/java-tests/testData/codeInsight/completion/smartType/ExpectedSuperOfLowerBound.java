import java.util.*;
class MyTest {
    <T> void addIfNotNull(T t, Collection<T> collection) {}
    {
        List<Object> l = null;
        addIfNotNull("someString", <caret>);
    }
}