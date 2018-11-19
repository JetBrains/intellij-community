// "Fix method 'foo' parameters with bounded wildcards" "true"
import java.util.List;

interface Xo {
    void foo(List<? super String> s);
}
public class ErrWarn implements Xo {
    public void <caret>foo(List<String> s) {

    }
}
class D extends ErrWarn {
    @Override
    public void foo(List<String> s) {
        super.foo(s);
    }
}
