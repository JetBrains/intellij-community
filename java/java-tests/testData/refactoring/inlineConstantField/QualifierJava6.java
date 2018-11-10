import java.util.*;

class A {
    private static final List<String> LIST = Collections.emptyList();

    void m(boolean b) {
        List<String> l = b ? new ArrayList<String>() : LI<caret>ST;
    }
}