// "Fix method 'foo' parameters with bounded wildcards" "true-preview"
import java.util.List;

interface Xo<T> {
    void foo(List<? super T> s);
}
public class ErrWarn implements Xo<String> {
    public void <caret>foo(List<? super String> s) {

    }
}
