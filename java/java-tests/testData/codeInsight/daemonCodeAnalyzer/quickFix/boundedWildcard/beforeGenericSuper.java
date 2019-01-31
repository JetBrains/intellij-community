// "Fix method 'foo' parameters with bounded wildcards" "true"
import java.util.List;

interface Xo<T> {
    void foo(List<? super T> s);
}
public class ErrWarn implements Xo<String> {
    public void <caret>foo(List<String> s) {

    }
}
