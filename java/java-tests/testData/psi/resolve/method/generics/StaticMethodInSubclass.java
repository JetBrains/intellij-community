import java.util.Collection;

public class SomeClass {
    public static Object find(Collection collection, Object criteria) {
        return criteria;
    }
    static class SomeSubClass extends SomeClass {
        public static <T> T find(Collection<T> collection, Object criteria) {
            return null;
        }
    }


    <T>void f()
    {
        Collection<T> c = null;
        Object criteria = null;

// IntelliJ finds the find(Collection, Object) method, and reports a non-existent compile error.
        T someInstance = SomeSubClass.<ref>find(c, criteria);
    }

}
