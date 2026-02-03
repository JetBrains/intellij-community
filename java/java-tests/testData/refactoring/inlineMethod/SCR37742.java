import java.util.List;
import java.util.ArrayList;

class Test {
    <T> void foo(T t) {
        List<? extends T> l = new ArrayList<T>();
    }

    void bar () {
        <caret>foo(new String());
    }
}
