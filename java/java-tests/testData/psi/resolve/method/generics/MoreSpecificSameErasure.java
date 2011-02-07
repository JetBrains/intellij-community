import java.util.*;

class SSS {
    public <T> void x(List<T> matcher) {
        System.out.println("<T>");
    }

    public int x(List<Long> matcher) {
        System.out.println("long");
        return 0;
    }

    public static <T> List<T> any(Class<T> type) {
        return null;
    }
    void f() {
        <ref>x(any(Long.class));
    }

}
